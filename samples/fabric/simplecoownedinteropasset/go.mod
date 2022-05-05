module github.com/hyperledger-labs/weaver-dlt-interoperability/samples/fabric/simplecoownedinteropasset

go 1.15

replace github.com/hyperledger-labs/weaver-dlt-interoperability/common/protos-go => ./protos-go
replace github.com/hyperledger-labs/weaver-dlt-interoperability/core/network/fabric-interop-cc/libs/assetexchange => ./libs/assetexchange
replace github.com/hyperledger-labs/weaver-dlt-interoperability/core/network/fabric-interop-cc/libs/testutils => ./libs/testutils

require (
	github.com/golang/protobuf v1.5.2
	github.com/hyperledger-labs/weaver-dlt-interoperability/common/protos-go v1.2.4
	github.com/hyperledger-labs/weaver-dlt-interoperability/core/network/fabric-interop-cc/libs/assetexchange v1.2.3
	github.com/hyperledger-labs/weaver-dlt-interoperability/core/network/fabric-interop-cc/libs/testutils v0.0.0-20210909191523-de832057a3ab
	github.com/hyperledger/fabric-chaincode-go v0.0.0-20210718160520-38d29fabecb9
	github.com/hyperledger/fabric-contract-api-go v1.1.1
	github.com/hyperledger/fabric-protos-go v0.0.0-20210720123151-f0dc3e2a0871
	github.com/sirupsen/logrus v1.8.1
	github.com/stretchr/testify v1.7.0
)
