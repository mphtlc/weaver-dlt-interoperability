package main_test

import (
	"crypto/sha256"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"testing"
	"time"

	"github.com/golang/protobuf/proto"
	"github.com/hyperledger-labs/weaver-dlt-interoperability/common/protos-go/common"
	"github.com/hyperledger-labs/weaver-dlt-interoperability/core/network/fabric-interop-cc/libs/assetexchange"
	sa "github.com/hyperledger-labs/weaver-dlt-interoperability/samples/fabric/simplecoownedinteropasset"
	mspProtobuf "github.com/hyperledger/fabric-protos-go/msp"
	"github.com/stretchr/testify/require"
	wtest "github.com/hyperledger-labs/weaver-dlt-interoperability/core/network/fabric-interop-cc/libs/testutils"
)

// function that supplies value that is to be returned by ctx.GetStub().GetCreator() in locker/recipient context
func getCreatorInContext(creator string) string {
	serializedIdentity := &mspProtobuf.SerializedIdentity{}
	var eCertBytes []byte
	if creator == "locker" {
		eCertBytes, _ = base64.StdEncoding.DecodeString(getLockerECertBase64())
	} else {
		eCertBytes, _ = base64.StdEncoding.DecodeString(getRecipientECertBase64())
	}
	serializedIdentity.IdBytes = eCertBytes
	serializedIdentity.Mspid = "ca.org1.example.com"
	serializedIdentityBytes, _ := proto.Marshal(serializedIdentity)

	return string(serializedIdentityBytes)
}

// function that supplies the ECert in base64 for locker (e.g., Alice)
func getLockerECertBase64() string {
	eCertBase64 := "LS0tLS1CRUdJTiBDRVJUSUZJQ0FURS0tLS0tCk1JSUNVVENDQWZpZ0F3SUJBZ0lSQU5qaWdnVHRhSERGRmtIaUI3VnhPN013Q2dZSUtvWkl6ajBFQXdJd2N6RUxNQWtHQTFVRUJoTUNWVk14RXpBUkJnTlZCQWdUQ2tOaGJHbG1iM0p1YVdFeEZqQVVCZ05WQkFjVERWTmhiaUJHY21GdVkybHpZMjh4R1RBWEJnTlZCQW9URUc5eVp6RXVaWGhoYlhCc1pTNWpiMjB4SERBYUJnTlZCQU1URTJOaExtOXlaekV1WlhoaGJYQnNaUzVqYjIwd0hoY05NVGt3TkRBeE1EZzBOVEF3V2hjTk1qa3dNekk1TURnME5UQXdXakJ6TVFzd0NRWURWUVFHRXdKVlV6RVRNQkVHQTFVRUNCTUtRMkZzYVdadmNtNXBZVEVXTUJRR0ExVUVCeE1OVTJGdUlFWnlZVzVqYVhOamJ6RVpNQmNHQTFVRUNoTVFiM0puTVM1bGVHRnRjR3hsTG1OdmJURWNNQm9HQTFVRUF4TVRZMkV1YjNKbk1TNWxlR0Z0Y0d4bExtTnZiVEJaTUJNR0J5cUdTTTQ5QWdFR0NDcUdTTTQ5QXdFSEEwSUFCT2VlYTRCNlM5ZTlyLzZUWGZFZUFmZ3FrNVdpcHZZaEdveGg1ZEZuK1g0bTN2UXZTQlhuVFdLVzczZVNnS0lzUHc5dExDVytwZW9yVnMxMWdieXdiY0dqYlRCck1BNEdBMVVkRHdFQi93UUVBd0lCcGpBZEJnTlZIU1VFRmpBVUJnZ3JCZ0VGQlFjREFnWUlLd1lCQlFVSEF3RXdEd1lEVlIwVEFRSC9CQVV3QXdFQi96QXBCZ05WSFE0RUlnUWcxYzJHZmJTa3hUWkxIM2VzUFd3c2llVkU1QWhZNHNPQjVGOGEvaHM5WjhVd0NnWUlLb1pJemowRUF3SURSd0F3UkFJZ1JkZ1krNW9iMDNqVjJLSzFWdjZiZE5xM2NLWHc0cHhNVXY5MFZOc0tHdTBDSUE4Q0lMa3ZEZWg3NEFCRDB6QUNkbitBTkMyVVQ2Sk5UNnd6VHNLN3BYdUwKLS0tLS1FTkQgQ0VSVElGSUNBVEUtLS0tLQ=="

	eCertBase64 = "LS0tLS1CRUdJTiBDRVJUSUZJQ0FURS0tLS0tCk1JSUNyVENDQWxTZ0F3SUJBZ0lVSENXLzBtV0xhc2hISG9zd0xxVWhpK1FwREc4d0NnWUlLb1pJemowRUF3SXcKY2pFTE1Ba0dBMVVFQmhNQ1ZWTXhGekFWQmdOVkJBZ1REazV2Y25Sb0lFTmhjbTlzYVc1aE1ROHdEUVlEVlFRSApFd1pFZFhKb1lXMHhHakFZQmdOVkJBb1RFVzl5WnpFdWJtVjBkMjl5YXpFdVkyOXRNUjB3R3dZRFZRUURFeFJqCllTNXZjbWN4TG01bGRIZHZjbXN4TG1OdmJUQWVGdzB5TURBM01qa3dORE0yTURCYUZ3MHlNVEEzTWprd05EUXgKTURCYU1GMHhDekFKQmdOVkJBWVRBbFZUTVJjd0ZRWURWUVFJRXc1T2IzSjBhQ0JEWVhKdmJHbHVZVEVVTUJJRwpBMVVFQ2hNTFNIbHdaWEpzWldSblpYSXhEekFOQmdOVkJBc1RCbU5zYVdWdWRERU9NQXdHQTFVRUF4TUZkWE5sCmNqRXdXVEFUQmdjcWhrak9QUUlCQmdncWhrak9QUU1CQndOQ0FBU3VoL3JWQ2Y4T0R1dzBJaG5yTTJpaWYyYTcKc0dUOEJJVjFQRURVM1NucUNsbWgrUlYvM0p5S2wvVHl0aHpOL1pWbktFL3R2NWQzZ1ZXYk5zdGM5NytTbzRIYwpNSUhaTUE0R0ExVWREd0VCL3dRRUF3SUhnREFNQmdOVkhSTUJBZjhFQWpBQU1CMEdBMVVkRGdRV0JCUXgvaExZCkNORzRlekNxdmdUS0MvV3d1U1ZubURBZkJnTlZIU01FR0RBV2dCVFdENjArZUNIYkR5RDMzUFdiQ3hWdVFxTUEKcVRBZkJnTlZIUkVFR0RBV2doUnZZelV4TURNM05EY3pPREF1YVdKdExtTnZiVEJZQmdncUF3UUZCZ2NJQVFSTQpleUpoZEhSeWN5STZleUpvWmk1QlptWnBiR2xoZEdsdmJpSTZJaUlzSW1obUxrVnVjbTlzYkcxbGJuUkpSQ0k2CkluVnpaWEl4SWl3aWFHWXVWSGx3WlNJNkltTnNhV1Z1ZENKOWZUQUtCZ2dxaGtqT1BRUURBZ05IQURCRUFpQUYKbnNMNlV1eFRtSks5bmhkTU1QNWxWN3hueVlsMVd5RGl6RVFzZnd1T1p3SWdYY3duSE9hVURXWWpmWHRGU0k1eQp6WjltcjZQRWtSNER0VEhJUkZhTVYxOD0KLS0tLS1FTkQgQ0VSVElGSUNBVEUtLS0tLQ=="

	return eCertBase64
}

// function that supplies the ECert in base64 for recipient (e.g., Bob)
func getRecipientECertBase64() string {
	eCertBase64 := "LS0tLS1CRUdJTiBDRVJUSUZJQ0FURS0tLS0tCk1JSUNVVENDQWZpZ0F3SUJBZ0lSQU5qaWdnVHRhSERGRmtIaUI3VnhPN013Q2dZSUtvWkl6ajBFQXdJd2N6RUxNQWtHQTFVRUJoTUNWVk14RXpBUkJnTlZCQWdUQ2tOaGJHbG1iM0p1YVdFeEZqQVVCZ05WQkFjVERWTmhiaUJHY21GdVkybHpZMjh4R1RBWEJnTlZCQW9URUc5eVp6RXVaWGhoYlhCc1pTNWpiMjB4SERBYUJnTlZCQU1URTJOaExtOXlaekV1WlhoaGJYQnNaUzVqYjIwd0hoY05NVGt3TkRBeE1EZzBOVEF3V2hjTk1qa3dNekk1TURnME5UQXdXakJ6TVFzd0NRWURWUVFHRXdKVlV6RVRNQkVHQTFVRUNCTUtRMkZzYVdadmNtNXBZVEVXTUJRR0ExVUVCeE1OVTJGdUlFWnlZVzVqYVhOamJ6RVpNQmNHQTFVRUNoTVFiM0puTVM1bGVHRnRjR3hsTG1OdmJURWNNQm9HQTFVRUF4TVRZMkV1YjNKbk1TNWxlR0Z0Y0d4bExtTnZiVEJaTUJNR0J5cUdTTTQ5QWdFR0NDcUdTTTQ5QXdFSEEwSUFCT2VlYTRCNlM5ZTlyLzZUWGZFZUFmZ3FrNVdpcHZZaEdveGg1ZEZuK1g0bTN2UXZTQlhuVFdLVzczZVNnS0lzUHc5dExDVytwZW9yVnMxMWdieXdiY0dqYlRCck1BNEdBMVVkRHdFQi93UUVBd0lCcGpBZEJnTlZIU1VFRmpBVUJnZ3JCZ0VGQlFjREFnWUlLd1lCQlFVSEF3RXdEd1lEVlIwVEFRSC9CQVV3QXdFQi96QXBCZ05WSFE0RUlnUWcxYzJHZmJTa3hUWkxIM2VzUFd3c2llVkU1QWhZNHNPQjVGOGEvaHM5WjhVd0NnWUlLb1pJemowRUF3SURSd0F3UkFJZ1JkZ1krNW9iMDNqVjJLSzFWdjZiZE5xM2NLWHc0cHhNVXY5MFZOc0tHdTBDSUE4Q0lMa3ZEZWg3NEFCRDB6QUNkbitBTkMyVVQ2Sk5UNnd6VHNLN3BYdUwKLS0tLS1FTkQgQ0VSVElGSUNBVEUtLS0tLQ="

	eCertBase64 = "LS0tLS1CRUdJTiBDRVJUSUZJQ0FURS0tLS0tCk1JSUNzekNDQWxxZ0F3SUJBZ0lVSjk3ZDJaWUNkRkNHbFo5L3hmZHRlcUdMc1Jvd0NnWUlLb1pJemowRUF3SXcKY2pFTE1Ba0dBMVVFQmhNQ1ZWTXhGekFWQmdOVkJBZ1REazV2Y25Sb0lFTmhjbTlzYVc1aE1ROHdEUVlEVlFRSApFd1pFZFhKb1lXMHhHakFZQmdOVkJBb1RFVzl5WnpFdWJtVjBkMjl5YXpFdVkyOXRNUjB3R3dZRFZRUURFeFJqCllTNXZjbWN4TG01bGRIZHZjbXN4TG1OdmJUQWVGdzB5TURBM01qa3dORE0yTURCYUZ3MHlNVEEzTWprd05EUXgKTURCYU1HQXhDekFKQmdOVkJBWVRBbFZUTVJjd0ZRWURWUVFJRXc1T2IzSjBhQ0JEWVhKdmJHbHVZVEVVTUJJRwpBMVVFQ2hNTFNIbHdaWEpzWldSblpYSXhEakFNQmdOVkJBc1RCV0ZrYldsdU1SSXdFQVlEVlFRREV3bHZjbWN4CllXUnRhVzR3V1RBVEJnY3Foa2pPUFFJQkJnZ3Foa2pPUFFNQkJ3TkNBQVFmbjRmVHRDclQ3WVMrZVI1WWRFVU8KMHRKWmJGaEtyYUdqeWVNM2tBTzNNN1VHdVBsUCtXcFdjNkNYUEx3bTNETHgrcjFhMUx6eW1KUWdaOVJjdXErcgpvNEhmTUlIY01BNEdBMVVkRHdFQi93UUVBd0lIZ0RBTUJnTlZIUk1CQWY4RUFqQUFNQjBHQTFVZERnUVdCQlM2ClkxR1FCMXAwUlNBeWxjTTRxQTlZS0JkU2hEQWZCZ05WSFNNRUdEQVdnQlRXRDYwK2VDSGJEeUQzM1BXYkN4VnUKUXFNQXFUQWZCZ05WSFJFRUdEQVdnaFJ2WXpVeE1ETTNORGN6T0RBdWFXSnRMbU52YlRCYkJnZ3FBd1FGQmdjSQpBUVJQZXlKaGRIUnljeUk2ZXlKb1ppNUJabVpwYkdsaGRHbHZiaUk2SWlJc0ltaG1Ma1Z1Y205c2JHMWxiblJKClJDSTZJbTl5WnpGaFpHMXBiaUlzSW1obUxsUjVjR1VpT2lKaFpHMXBiaUo5ZlRBS0JnZ3Foa2pPUFFRREFnTkgKQURCRUFpQkwrSzAzVGFFeWJaRkdWMmMzSS81ZXlpMFBveGc2elZOWDJkajJWRlk5WWdJZ0w5ZlhzcWhaUEU0VApBSkU4ZVZqdWZaOVJnNERJWWloTVVTKzBPbGpWL3pBPQotLS0tLUVORCBDRVJUSUZJQ0FURS0tLS0t"

	return eCertBase64
}

// function to generate a "SHA256" hash in base64 format for a given preimage
func generateSHA256HashInBase64Form(preimage string) string {
	hasher := sha256.New()
	hasher.Write([]byte(preimage))
	shaHash := hasher.Sum(nil)
	shaHashBase64 := base64.StdEncoding.EncodeToString(shaHash)
	return shaHashBase64
}

type ContractedFungibleAsset struct {
	Type     string `json:"type"`
	NumUnits uint64 `json:"id"`
}

// test case for "asset lock and claim" happy path
func TestLockAndClaimBondSharedAsset(t *testing.T) {
	ctx, chaincodeStub := wtest.PrepMockStub()
	sc := sa.SmartContract{}

	bondLocker := getLockerECertBase64()
	bondRecipient := getRecipientECertBase64()
	bondType := "bond"
	bondId := "b01"
	bondIssuer := "network1"
	bondFaceValue := 1
	currentTime := time.Now()
	bondMaturityDate := currentTime.Add(time.Hour * 24) // maturity date is 1 day after current time

	// Create bond asset
	// let ctx.GetStub().GetState() return that the bond asset didn't exist before
	chaincodeStub.GetStateReturnsOnCall(0, nil, nil)
	err := sc.CreateSharedAsset(ctx, bondType, bondId, []string {bondLocker}, bondIssuer, bondFaceValue, bondMaturityDate.Format(time.RFC822))
	require.NoError(t, err)
	fmt.Println("*** Created shared bond asset in network1 owned by Alice ***")

	// Lock bond asset in network1 by Alice for Bob
	fmt.Println("*** Lock bond asset in network1 by Alice ***")
	preimage := "abcd"
	hashBase64 := generateSHA256HashInBase64Form(preimage)
	defaultTimeLockSecs := uint64(300) // set default locking period as 5 minutes
	currentTimeSecs := uint64(time.Now().Unix())
	bondContractId := "bond-contract"
	lockInfoHTLC := &common.AssetLockHTLC{
		HashBase64:     []byte(hashBase64),
		ExpiryTimeSecs: currentTimeSecs + defaultTimeLockSecs,
		TimeSpec:       common.AssetLockHTLC_EPOCH,
	}
	lockInfoHTLCBytes, _ := proto.Marshal(lockInfoHTLC)
	lockInfo := &common.AssetLock{
		LockInfo: lockInfoHTLCBytes,
	}
	lockInfoBytes, _ := proto.Marshal(lockInfo)
	bondAgreement := &common.AssetExchangeAgreement{
		Type:      bondType,
		Id:        bondId,
		Locker:    bondLocker,
		//Recipient: bondRecipient + "," + bondLocker,
		Recipient: bondLocker + "," + bondRecipient,
	}
	bondAgreementBytes, _ := proto.Marshal(bondAgreement)
	bondAsset := sa.BondAsset{
		Type:         bondType,
		ID:           bondId,
		CoOwners:     []string {bondLocker},
		Issuer:       bondIssuer,
		FaceValue:    bondFaceValue,
		MaturityDate: bondMaturityDate,
	}
	bondAssetBytes, _ := json.Marshal(bondAsset)
	chaincodeStub.GetCreatorReturnsOnCall(0, []byte(getCreatorInContext("locker")), nil)
	chaincodeStub.GetStateReturnsOnCall(1, bondAssetBytes, nil)
	//chaincodeStub.InvokeChaincodeReturns(shim.Success([]byte(bondContractId)))

	chaincodeStub.GetCreatorReturnsOnCall(1, []byte(getCreatorInContext("locker")), nil)
	// chaincodeStub.GetStateReturns should return nil to be able to lock the asset
	chaincodeStub.GetStateReturnsOnCall(2, nil, nil)
	bondContractId, err = sc.LockSharedAsset(ctx, base64.StdEncoding.EncodeToString(bondAgreementBytes), base64.StdEncoding.EncodeToString(lockInfoBytes))
	require.NoError(t, err)
	require.NotEmpty(t, bondContractId)
	fmt.Println("*** Lock bond asset in network1 by Alice with contractId: ", bondContractId)

	// Claim phase begins.
	// Claim bond asset in network1 by Bob
	fmt.Println("*** Claim bond asset in network1 by Bob ***")
	preimageBase64 := base64.StdEncoding.EncodeToString([]byte(preimage))
        claimInfoHTLC := &common.AssetClaimHTLC{
                HashPreimageBase64: []byte(preimageBase64),
        }
        claimInfoHTLCBytes, _ := proto.Marshal(claimInfoHTLC)
        claimInfo := &common.AssetClaim{
                ClaimInfo:     claimInfoHTLCBytes,
                LockMechanism: common.LockMechanism_HTLC,
        }
        claimInfoBytes, _ := proto.Marshal(claimInfo)
	//chaincodeStub.InvokeChaincodeReturns(shim.Success(nil))

	chaincodeStub.GetCreatorReturnsOnCall(2, []byte(getCreatorInContext("recipient")), nil)
	hashLock := assetexchange.HashLock{HashBase64: hashBase64}
	var lockInfoVal interface{}
	lockInfoVal = hashLock
	assetLockVal := assetexchange.SharedAssetLockValue{Lockers: []string {bondLocker}, Recipients: []string {bondRecipient, bondLocker}, LockInfo: lockInfoVal, ExpiryTimeSecs: currentTimeSecs + defaultTimeLockSecs}
	assetLockValBytes, _ := json.Marshal(assetLockVal)
	chaincodeStub.GetStateReturnsOnCall(3, assetLockValBytes, nil)
	chaincodeStub.DelStateReturnsOnCall(0, nil)
	chaincodeStub.DelStateReturnsOnCall(1, nil)

	chaincodeStub.GetCreatorReturnsOnCall(3, []byte(getCreatorInContext("recipient")), nil)
	chaincodeStub.GetStateReturnsOnCall(4, bondAssetBytes, nil)
	chaincodeStub.GetStateReturnsOnCall(5, []byte(bondContractId), nil)
	chaincodeStub.GetStateReturnsOnCall(6, assetLockValBytes, nil)
	isClaimed, err := sc.ClaimSharedAsset(ctx, base64.StdEncoding.EncodeToString(bondAgreementBytes), base64.StdEncoding.EncodeToString(claimInfoBytes))
	require.NoError(t, err)
	require.True(t, isClaimed)
	fmt.Println("*** Claimed bond asset in network1 by Bob ***")

}
