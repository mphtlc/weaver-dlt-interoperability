# Network1
./bin/fabric-cli user add --target-network=network1 --id=alice --secret=alicepw
./bin/fabric-cli user add --target-network=network1 --id=bob --secret=bobpw
./bin/fabric-cli configure asset add --target-network=network1 --type=sharedBond --data-file=./src/data/sharedassets.json 

# Network2
#./bin/fabric-cli user add --target-network=network2 --id=alice --secret=alicepw
#./bin/fabric-cli user add --target-network=network2 --id=bob --secret=bobpw
#./bin/fabric-cli configure asset add --target-network=network2 --type=token --data-file=./src/data/tokens.json
