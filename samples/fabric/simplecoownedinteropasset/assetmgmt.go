/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package main

import (
	"encoding/base64"
	"encoding/json"
	"strings"
	"fmt"

	"github.com/golang/protobuf/proto"
	"github.com/hyperledger-labs/weaver-dlt-interoperability/common/protos-go/common"
	"github.com/hyperledger-labs/weaver-dlt-interoperability/core/network/fabric-interop-cc/libs/assetexchange"
	"github.com/hyperledger/fabric-contract-api-go/contractapi"
	log "github.com/sirupsen/logrus"
)

func ifEqualSets(a, b []string) bool {
    if len(a) != len(b) {
        return false
    }

    astring := strings.Join(a, ",")
    bstring := strings.Join(b, ",")
    for i := range a {
        // ensure that each element of a is also an element of b
        if !strings.Contains(bstring, a[i]) {
            return false
        }
    }

    for i := range a {
        // ensure that each element of b is also an element of a
        if !strings.Contains(astring, b[i]) {
            return false
        }
    }
    return true
}

// asset specific checks (ideally an asset in a different application might implement checks specific to that asset)
func (s *SmartContract) BondAssetSpecificChecks(ctx contractapi.TransactionContextInterface, assetType, id, locker string, lockInfoSerializedProto64 string) error {

	lockInfo := &common.AssetLock{}
	// Decoding from base64
	lockInfoSerializedProto, err := base64.StdEncoding.DecodeString(lockInfoSerializedProto64)
	if err != nil {
		return logThenErrorf(err.Error())
	}
	if len(lockInfoSerializedProto) == 0 {
		return logThenErrorf("empty lock info")
	}
	err = proto.Unmarshal([]byte(lockInfoSerializedProto), lockInfo)
	if err != nil {
		return logThenErrorf(err.Error())
	}

	lockInfoHTLC := &common.AssetLockHTLC{}
	err = proto.Unmarshal(lockInfo.LockInfo, lockInfoHTLC)
	if err != nil {
		return logThenErrorf("unmarshal error: %+v", err)
	}
	// ReadAsset should check both the existence and ownership of the asset for the locker
	bond, err := s.ReadSharedAsset(ctx, assetType, id, false)
	if err != nil {
		return logThenErrorf("failed reading the bond asset: %+v", err)
	}
	log.Infof("bond: %+v", *bond)
	log.Infof("lockInfoHTLC: %+v", *lockInfoHTLC)

	if !ifEqualSets(bond.CoOwners, strings.Split(locker, ",")) {
		return logThenErrorf("cannot lock shared bond asset as the assetAgreement.Locker doesn't include all the coOwners of the asset")
	}

	// Check if asset doesn't mature before locking period
	if uint64(bond.MaturityDate.Unix()) < lockInfoHTLC.ExpiryTimeSecs {
		return logThenErrorf("cannot lock bond asset as it will mature before locking period")
	}

	return nil
}

// Ledger transaction (invocation) functions

func (s *SmartContract) LockSharedAsset(ctx contractapi.TransactionContextInterface, assetExchangeAgreementSerializedProto64 string, lockInfoSerializedProto64 string) (string, error) {

	assetAgreement, err := s.ValidateAndExtractAssetAgreement(assetExchangeAgreementSerializedProto64)
	if err != nil {
		return "", err
	}
	err = s.BondAssetSpecificChecks(ctx, assetAgreement.Type, assetAgreement.Id, assetAgreement.Locker, lockInfoSerializedProto64)
	if err != nil {
		return "", logThenErrorf(err.Error())
	}

	contractId, err := assetexchange.LockSharedAsset(ctx, "", assetExchangeAgreementSerializedProto64, lockInfoSerializedProto64)
	if err != nil {
		return "", logThenErrorf(err.Error())
	}

	// write to the ledger the details needed at the time of unlock/claim
	err = s.ContractIdAssetsLookupMap(ctx, assetAgreement.Type, assetAgreement.Id, contractId)
	if err != nil {
		return "", logThenErrorf(err.Error())
	}

	return contractId, nil
}

// Check whether this asset has been locked by anyone (not just by caller)
func (s *SmartContract) IsSharedAssetLocked(ctx contractapi.TransactionContextInterface, assetAgreementSerializedProto64 string) (bool, error) {
	return assetexchange.IsSharedAssetLocked(ctx, "", assetAgreementSerializedProto64)
}

// Check whether a bond asset has been locked using contractId by anyone (not just by caller)
func (s *SmartContract) IsSharedAssetLockedQueryUsingContractId(ctx contractapi.TransactionContextInterface, contractId string) (bool, error) {
	return assetexchange.IsSharedAssetLockedQueryUsingContractId(ctx, contractId)
}

func (s *SmartContract) ClaimSharedAsset(ctx contractapi.TransactionContextInterface, assetAgreementSerializedProto64 string, claimInfoSerializedProto64 string) (bool, error) {
	assetAgreement, err := s.ValidateAndExtractAssetAgreement(assetAgreementSerializedProto64)
	if err != nil {
		return false, err
	}
	claimed := false
	_, err = assetexchange.ClaimSharedAsset(ctx, "", assetAgreementSerializedProto64, claimInfoSerializedProto64)
	if err != nil {
		return false, logThenErrorf(err.Error())
	} else {
		claimed = true
	}
	if claimed {
		// Change asset ownership to claimant
		recipientECertBase64, err := getECertOfTxCreatorBase64(ctx)
		if err != nil {
			return false, logThenErrorf(err.Error())
		}
		asset, err := s.ReadSharedAsset(ctx, assetAgreement.Type, assetAgreement.Id, true)
		if err != nil {
			return false, logThenErrorf(err.Error())
		}
		if !strings.Contains(assetAgreement.Recipient, string(recipientECertBase64)) {
			fmt.Printf("recipients[0]: %s \n recipient: %s", assetAgreement.Recipient, recipientECertBase64)
			return false, logThenErrorf("cannot claim as the transaction creator %s is not one of the recipient coOwners", string(recipientECertBase64))
		}
		var recipients []string
		recipients = strings.Split(assetAgreement.Recipient, ",")
		asset.CoOwners = recipients
		assetJSON, err := json.Marshal(asset)
		if err != nil {
			return false, logThenErrorf(err.Error())
		}
		err = ctx.GetStub().PutState(getBondAssetKey(assetAgreement.Type, assetAgreement.Id), assetJSON)
		if err != nil {
			return false, logThenErrorf(err.Error())
		}

		err = s.DeleteAssetLookupMaps(ctx, assetAgreement.Type, assetAgreement.Id)
		if err != nil {
			return false, logThenErrorf("failed to delete bond asset lookup maps: %+v", err)
		}

		return true, nil
	} else {
		return false, logThenErrorf("claim on bond asset type %s with asset id %s failed", assetAgreement.Type, assetAgreement.Id)
	}
}

func (s *SmartContract) ClaimAssetUsingContractId(ctx contractapi.TransactionContextInterface, contractId, claimInfoSerializedProto64 string) (bool, error) {
	claimed := false
	assetLockVal, err := assetexchange.ClaimSharedAssetUsingContractId(ctx, contractId, claimInfoSerializedProto64)
	if err != nil {
		return false, logThenErrorf(err.Error())
	} else {
		claimed = true
	}
	if claimed {
		// Change asset ownership to claimant
		recipientECertBase64, err := getECertOfTxCreatorBase64(ctx)
		if err != nil {
			return false, logThenErrorf(err.Error())
		}

		// Fetch the contracted bond asset type from the ledger
		assetType, err := s.FetchAssetTypeFromContractIdAssetLookupMap(ctx, contractId)
		if err != nil {
			return false, logThenErrorf(err.Error())
		}
		// Fetch the contracted bond asset id from the ledger
		assetId, err := s.FetchAssetIdFromContractIdAssetLookupMap(ctx, contractId)
		if err != nil {
			return false, logThenErrorf(err.Error())
		}

		asset, err := s.ReadSharedAsset(ctx, assetType, assetId, true)
		if err != nil {
			return false, logThenErrorf(err.Error())
		}
		asset.CoOwners = assetLockVal.Recipients
		if !strings.Contains(strings.Join(assetLockVal.Recipients, ","), string(recipientECertBase64)) {
			fmt.Printf("recipients[0]: %s \n recipient: %s", assetLockVal.Recipients[0], recipientECertBase64)
			return false, logThenErrorf("cannot claim as the transaction creator %s is not one of the recipient coOwners", string(recipientECertBase64))
		}

		assetJSON, err := json.Marshal(asset)
		if err != nil {
			return false, logThenErrorf(err.Error())
		}
		err = ctx.GetStub().PutState(getBondAssetKey(assetType, assetId), assetJSON)
		if err != nil {
			return false, logThenErrorf(err.Error())
		}
		// delete the lookup maps
		err = s.DeleteAssetLookupMapsUsingContractId(ctx, assetType, assetId, contractId)
		if err != nil {
			return false, logThenErrorf(err.Error())
		}

		return true, nil
	} else {
		return false, logThenErrorf("claim on bond asset using contractId %s failed", contractId)
	}
}

func (s *SmartContract) UnlockSharedAsset(ctx contractapi.TransactionContextInterface, assetAgreementSerializedProto64 string) (bool, error) {
	assetAgreement, err := s.ValidateAndExtractAssetAgreement(assetAgreementSerializedProto64)
	if err != nil {
		return false, err
	}

	unlocked := false
	_, err = assetexchange.UnlockSharedAsset(ctx, "", assetAgreementSerializedProto64)
	if err != nil {
		return false, logThenErrorf(err.Error())
	} else {
		unlocked = true
	}
	if unlocked {
		err = s.DeleteAssetLookupMaps(ctx, assetAgreement.Type, assetAgreement.Id)
		if err != nil {
			return false, logThenErrorf("failed to delete bond asset lookup maps: %+v", err)
		}
	} else {
		return false, logThenErrorf("unlock on bond asset type %s with asset id %s failed", assetAgreement.Type, assetAgreement.Id)
	}

	return true, nil
}

func (s *SmartContract) UnlockSharedAssetUsingContractId(ctx contractapi.TransactionContextInterface, contractId string) (bool, error) {
	unlocked := false
	err := assetexchange.UnlockSharedAssetUsingContractId(ctx, contractId)
	if err != nil {
		return false, logThenErrorf(err.Error())
	} else {
		unlocked = true
	}
	if unlocked {
		// delete the lookup maps
		err := s.DeleteAssetLookupMapsOnlyUsingContractId(ctx, contractId)
		if err != nil {
			return false, logThenErrorf(err.Error())
		}
		return true, nil
	} else {
		return false, logThenErrorf("unlock on bond asset using contractId %s failed", contractId)
	}
}
