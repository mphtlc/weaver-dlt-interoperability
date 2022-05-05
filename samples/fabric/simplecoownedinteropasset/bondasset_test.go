package main_test

import (
	"encoding/json"
	"fmt"
	"testing"
	"time"
	"encoding/base64"

        "github.com/golang/protobuf/proto"
        mspProtobuf "github.com/hyperledger/fabric-protos-go/msp"

	"github.com/hyperledger/fabric-protos-go/ledger/queryresult"
	sa "github.com/hyperledger-labs/weaver-dlt-interoperability/samples/fabric/simplecoownedinteropasset"
	"github.com/stretchr/testify/require"
	wtest "github.com/hyperledger-labs/weaver-dlt-interoperability/core/network/fabric-interop-cc/libs/testutils"
	wtestmocks "github.com/hyperledger-labs/weaver-dlt-interoperability/core/network/fabric-interop-cc/libs/testutils/mocks"
)

const (
	defaultAssetType    = "BearerBonds"
	defaultAssetId      = "asset1"
	defaultAssetOwner   = "Alice"
	defaultAssetIssuer  = "Treasury"
)

func TestInitBondSharedAssetLedger(t *testing.T) {
	transactionContext, chaincodeStub := wtest.PrepMockStub()
	simpleAsset := sa.SmartContract{}

	err := simpleAsset.InitBondSharedAssetLedger(transactionContext)
	require.NoError(t, err)

	chaincodeStub.PutStateReturns(fmt.Errorf("failed inserting key"))
	err = simpleAsset.InitBondSharedAssetLedger(transactionContext)
	require.EqualError(t, err, "failed to put to world state. failed inserting key")
}

func TestCreateSharedAsset(t *testing.T) {
	transactionContext, chaincodeStub := wtest.PrepMockStub()
	simpleAsset := sa.SmartContract{}

	err := simpleAsset.CreateSharedAsset(transactionContext, "", "", []string {""}, "", 0, "02 Jan 26 15:04 MST")
	require.Error(t, err)

	err = simpleAsset.CreateSharedAsset(transactionContext, defaultAssetType, "", []string {""}, "", 0, "02 Jan 26 15:04 MST")
	require.Error(t, err)

	err = simpleAsset.CreateSharedAsset(transactionContext, defaultAssetType, defaultAssetId, []string {}, "", 0, "02 Jan 26 15:04 MST")
	require.Error(t, err)

	err = simpleAsset.CreateSharedAsset(transactionContext, defaultAssetType, defaultAssetId, []string {defaultAssetOwner}, "", 0, "02 Jan 26 15:04 MST")
	require.NoError(t, err)

	err = simpleAsset.CreateSharedAsset(transactionContext, defaultAssetType, defaultAssetId, []string {""}, defaultAssetIssuer, 0, "02 Jan 26 15:04 MST")
	require.NoError(t, err)

	err = simpleAsset.CreateSharedAsset(transactionContext, defaultAssetType, defaultAssetId, []string {defaultAssetOwner}, "", 0, "02 Jan 06 15:04 MST")
	require.EqualError(t, err, "maturity date can not be in past.")

	err = simpleAsset.CreateSharedAsset(transactionContext, defaultAssetType, defaultAssetId, []string {defaultAssetOwner}, "", 0, "")
	require.EqualError(t, err, "maturity date provided is not in correct format, please use this format: 02 Jan 06 15:04 MST")

	chaincodeStub.GetStateReturns([]byte{}, nil)
	err = simpleAsset.CreateSharedAsset(transactionContext, defaultAssetType, defaultAssetId, []string {defaultAssetOwner}, "", 0, "")
	require.EqualError(t, err, "the asset asset1 already exists")

	chaincodeStub.GetStateReturns(nil, fmt.Errorf("unable to retrieve asset"))
	err = simpleAsset.CreateSharedAsset(transactionContext, defaultAssetType, defaultAssetId, []string {defaultAssetOwner}, "", 0, "")
	require.EqualError(t, err, "failed to read asset record from world state: unable to retrieve asset")
}

func TestReadSharedAsset(t *testing.T) {
	transactionContext, chaincodeStub := wtest.PrepMockStub()
	simpleAsset := sa.SmartContract{}

	expectedAsset := &sa.BondAsset{ID: "asset1"}
	bytes, err := json.Marshal(expectedAsset)
	require.NoError(t, err)

	chaincodeStub.GetStateReturns(bytes, nil)
	asset, err := simpleAsset.ReadSharedAsset(transactionContext, "", "", false)
	require.NoError(t, err)
	require.Equal(t, expectedAsset, asset)

	chaincodeStub.GetStateReturns(nil, fmt.Errorf("unable to retrieve asset"))
	_, err = simpleAsset.ReadSharedAsset(transactionContext, "", "", false)
	require.EqualError(t, err, "failed to read asset record from world state: unable to retrieve asset")

	chaincodeStub.GetStateReturns(nil, nil)
	asset, err = simpleAsset.ReadSharedAsset(transactionContext, "", "asset1", false)
	require.EqualError(t, err, "the asset asset1 does not exist")
	require.Nil(t, asset)
}

func TestUpdateFaceValueOfSharedAsset(t *testing.T) {
	transactionContext, chaincodeStub := wtest.PrepMockStub()
	simpleAsset := sa.SmartContract{}

	expectedAsset := &sa.BondAsset{ID: "asset1", CoOwners: []string {getTestTxCreatorECertBase64()}}
	bytes, err := json.Marshal(expectedAsset)
	require.NoError(t, err)

	chaincodeStub.GetStateReturns(bytes, nil)
	chaincodeStub.GetCreatorReturns([]byte(getCreator()), nil)
	err = simpleAsset.UpdateFaceValue(transactionContext, "", "asset1", 0)
	require.NoError(t, err)

	chaincodeStub.GetStateReturns(nil, nil)
	err = simpleAsset.UpdateFaceValue(transactionContext, "", "asset1", 0)
	require.EqualError(t, err, "the asset asset1 does not exist")

	chaincodeStub.GetStateReturns(nil, fmt.Errorf("unable to retrieve asset"))
	err = simpleAsset.UpdateFaceValue(transactionContext, "", "asset1", 0)
	require.EqualError(t, err, "failed to read asset record from world state: unable to retrieve asset")
}

func TestUpdateMaturityDateOfSharedAsset(t *testing.T) {
	transactionContext, chaincodeStub := wtest.PrepMockStub()
	simpleAsset := sa.SmartContract{}

	expectedAsset := &sa.BondAsset{ID: "asset1", CoOwners: []string {getTestTxCreatorECertBase64()}}
	bytes, err := json.Marshal(expectedAsset)
	require.NoError(t, err)

	chaincodeStub.GetStateReturns(bytes, nil)
	chaincodeStub.GetCreatorReturns([]byte(getCreator()), nil)
	err = simpleAsset.UpdateMaturityDate(transactionContext, "", "asset1", time.Now())
	require.NoError(t, err)

	chaincodeStub.GetStateReturns(nil, nil)
	err = simpleAsset.UpdateMaturityDate(transactionContext, "", "asset1", time.Now())
	require.EqualError(t, err, "the asset asset1 does not exist")

	chaincodeStub.GetStateReturns(nil, fmt.Errorf("unable to retrieve asset"))
	err = simpleAsset.UpdateMaturityDate(transactionContext, "", "asset1", time.Now())
	require.EqualError(t, err, "failed to read asset record from world state: unable to retrieve asset")
}

func TestDeleteSharedAsset(t *testing.T) {
	transactionContext, chaincodeStub := wtest.PrepMockStub()
	simpleAsset := sa.SmartContract{}

	//asset := &sa.BondAsset{ID: "asset1"}
	asset := &sa.BondAsset{ID: "asset1", CoOwners: []string {getTestTxCreatorECertBase64()}}
	bytes, err := json.Marshal(asset)
	require.NoError(t, err)

	chaincodeStub.GetStateReturns(bytes, nil)
	chaincodeStub.GetCreatorReturns([]byte(getCreator()), nil)
	chaincodeStub.DelStateReturns(nil)
	err = simpleAsset.DeleteAsset(transactionContext, "", "")
	require.NoError(t, err)

	chaincodeStub.GetStateReturns(nil, nil)
	err = simpleAsset.DeleteAsset(transactionContext, "", "asset1")
	require.EqualError(t, err, "the bond asset of type " + "" + " and id " + "asset1" + " does not exist")

	chaincodeStub.GetStateReturns(nil, fmt.Errorf("unable to retrieve asset"))
	err = simpleAsset.DeleteAsset(transactionContext, "", "")
	require.EqualError(t, err, "failed to read asset record from world state: unable to retrieve asset")
}

func TestUpdateCoOwners(t *testing.T) {
	transactionContext, chaincodeStub := wtest.PrepMockStub()
	simpleAsset := sa.SmartContract{}

	asset := &sa.BondAsset{ID: "asset1"}
	bytes, err := json.Marshal(asset)
	require.NoError(t, err)

	chaincodeStub.GetStateReturns(bytes, nil)
	err = simpleAsset.UpdateCoOwners(transactionContext, "", "", []string {""})
	require.NoError(t, err)

	chaincodeStub.GetStateReturns(nil, fmt.Errorf("unable to retrieve asset"))
	err = simpleAsset.UpdateCoOwners(transactionContext, "", "", []string {""})
	require.EqualError(t, err, "failed to read asset record from world state: unable to retrieve asset")
}

func TestGetMySharedAssets(t *testing.T) {
	transactionContext, chaincodeStub := wtest.PrepMockStub()
	simpleAsset := sa.SmartContract{}
	iterator := &wtestmocks.StateQueryIterator{}

	asset := &sa.BondAsset{ID: "asset1", CoOwners: []string {getTestTxCreatorECertBase64()}}
	bytes, err := json.Marshal(asset)
	require.NoError(t, err)

	iterator.HasNextReturnsOnCall(0, true)
	iterator.HasNextReturnsOnCall(1, false)
	iterator.NextReturns(&queryresult.KV{Value: bytes}, nil)

	chaincodeStub.GetCreatorReturns([]byte(getCreator()), nil)

	chaincodeStub.GetStateByRangeReturns(iterator, nil)
	assets, err := simpleAsset.GetAllAssets(transactionContext)
	require.NoError(t, err)
	require.Equal(t, []*sa.BondAsset{asset}, assets)

	iterator.HasNextReturns(true)
	iterator.NextReturns(nil, fmt.Errorf("failed retrieving next item"))
	assets, err = simpleAsset.GetAllAssets(transactionContext)
	require.EqualError(t, err, "failed retrieving next item")
	require.Nil(t, assets)

	chaincodeStub.GetStateByRangeReturns(nil, fmt.Errorf("failed retrieving all assets"))
	assets, err = simpleAsset.GetAllAssets(transactionContext)
	require.EqualError(t, err, "failed retrieving all assets")
	require.Nil(t, assets)
}

func TestGetAllSharedAssets(t *testing.T) {
	transactionContext, chaincodeStub := wtest.PrepMockStub()
	simpleAsset := sa.SmartContract{}
	iterator := &wtestmocks.StateQueryIterator{}

	asset := &sa.BondAsset{ID: "asset1"}
	bytes, err := json.Marshal(asset)
	require.NoError(t, err)

	iterator.HasNextReturnsOnCall(0, true)
	iterator.HasNextReturnsOnCall(1, false)
	iterator.NextReturns(&queryresult.KV{Value: bytes}, nil)

	chaincodeStub.GetStateByRangeReturns(iterator, nil)
	assets, err := simpleAsset.GetAllAssets(transactionContext)
	require.NoError(t, err)
	require.Equal(t, []*sa.BondAsset{asset}, assets)

	iterator.HasNextReturns(true)
	iterator.NextReturns(nil, fmt.Errorf("failed retrieving next item"))
	assets, err = simpleAsset.GetAllAssets(transactionContext)
	require.EqualError(t, err, "failed retrieving next item")
	require.Nil(t, assets)

	chaincodeStub.GetStateByRangeReturns(nil, fmt.Errorf("failed retrieving all assets"))
	assets, err = simpleAsset.GetAllAssets(transactionContext)
	require.EqualError(t, err, "failed retrieving all assets")
	require.Nil(t, assets)
}

// function that supplies value that is to be returned by ctx.GetStub().GetCreator()
func getCreator() string {
        serializedIdentity := &mspProtobuf.SerializedIdentity{}
        eCertBytes, _ := base64.StdEncoding.DecodeString(getTestTxCreatorECertBase64())
        serializedIdentity.IdBytes = []byte(eCertBytes)
        serializedIdentity.Mspid = "ca.org1.example.com"
        serializedIdentityBytes, _ := proto.Marshal(serializedIdentity)

        return string(serializedIdentityBytes)
}

// function that supplies the ECert in base64 for the transaction creator
func getTestTxCreatorECertBase64() string {
        eCertBase64 := "LS0tLS1CRUdJTiBDRVJUSUZJQ0FURS0tLS0tCk1JSUNVVENDQWZpZ0F3SUJBZ0lSQU5qaWdnVHRhSERGRmtIaUI3VnhPN013Q2dZSUtvWkl6ajBFQXdJd2N6RUxNQWtHQTFVRUJoTUNWVk14RXpBUkJnTlZCQWdUQ2tOaGJHbG1iM0p1YVdFeEZqQVVCZ05WQkFjVERWTmhiaUJHY21GdVkybHpZMjh4R1RBWEJnTlZCQW9URUc5eVp6RXVaWGhoYlhCc1pTNWpiMjB4SERBYUJnTlZCQU1URTJOaExtOXlaekV1WlhoaGJYQnNaUzVqYjIwd0hoY05NVGt3TkRBeE1EZzBOVEF3V2hjTk1qa3dNekk1TURnME5UQXdXakJ6TVFzd0NRWURWUVFHRXdKVlV6RVRNQkVHQTFVRUNCTUtRMkZzYVdadmNtNXBZVEVXTUJRR0ExVUVCeE1OVTJGdUlFWnlZVzVqYVhOamJ6RVpNQmNHQTFVRUNoTVFiM0puTVM1bGVHRnRjR3hsTG1OdmJURWNNQm9HQTFVRUF4TVRZMkV1YjNKbk1TNWxlR0Z0Y0d4bExtTnZiVEJaTUJNR0J5cUdTTTQ5QWdFR0NDcUdTTTQ5QXdFSEEwSUFCT2VlYTRCNlM5ZTlyLzZUWGZFZUFmZ3FrNVdpcHZZaEdveGg1ZEZuK1g0bTN2UXZTQlhuVFdLVzczZVNnS0lzUHc5dExDVytwZW9yVnMxMWdieXdiY0dqYlRCck1BNEdBMVVkRHdFQi93UUVBd0lCcGpBZEJnTlZIU1VFRmpBVUJnZ3JCZ0VGQlFjREFnWUlLd1lCQlFVSEF3RXdEd1lEVlIwVEFRSC9CQVV3QXdFQi96QXBCZ05WSFE0RUlnUWcxYzJHZmJTa3hUWkxIM2VzUFd3c2llVkU1QWhZNHNPQjVGOGEvaHM5WjhVd0NnWUlLb1pJemowRUF3SURSd0F3UkFJZ1JkZ1krNW9iMDNqVjJLSzFWdjZiZE5xM2NLWHc0cHhNVXY5MFZOc0tHdTBDSUE4Q0lMa3ZEZWg3NEFCRDB6QUNkbitBTkMyVVQ2Sk5UNnd6VHNLN3BYdUwKLS0tLS1FTkQgQ0VSVElGSUNBVEUtLS0tLQ=="

        return eCertBase64
}
