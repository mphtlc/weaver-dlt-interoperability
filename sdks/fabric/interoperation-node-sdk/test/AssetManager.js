/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

/* eslint-disable no-unused-expressions */

const fs = require("fs");
const keyutil = require("jsrsasign").KEYUTIL;
const sinon = require("sinon");
const chai = require("chai");
const chaiAsPromised = require("chai-as-promised");

chai.use(chaiAsPromised);
const { expect } = chai;
chai.should();

const { Wallets } = require("fabric-network");
const { ContractImpl } = require("fabric-network/lib/contract");
const { NetworkImpl } = require("fabric-network/lib/network");
const assetManager = require("../src/AssetManager");
import assetLocksPb from "@hyperledger-labs/weaver-protos-js/common/asset_locks_pb";

describe("AssetManager", () => {
    const mspId = "mspId";
    const foreignNetworkId = "foreignNetworkId";
    const userName = "user_name";

    const assetType = "bond";
    const assetID = "A001";
    const fungibleAssetType = "cbdc";
    const numUnits = 1000;
    const recipientECert = fs.readFileSync(`${__dirname}/data/anotherSignCert.pem`).toString();
    let lockerECert;

    let wallet;
    let amc;
    // Initialize wallet with a single user identity
    async function initializeWallet() {
        const privKeyFile = `${__dirname}/data/privKey.pem`;
        const signCertFile = `${__dirname}/data/signCert.pem`;
        const privateKeyStr = fs.readFileSync(privKeyFile).toString();
        const signCert = fs.readFileSync(signCertFile).toString();
        lockerECert = signCert;
        wallet = await Wallets.newInMemoryWallet();
        const userIdentity = {
            credentials: { certificate: signCert, privateKey: privateKeyStr },
            mspId,
            type: "X.509",
        };
        await wallet.put(userName, userIdentity);
        return userIdentity;
    }

    async function sleep(ms) {
        return new Promise(resolve => setTimeout(resolve, ms));
    }

    beforeEach(async () => {
        await initializeWallet();
        const network = sinon.createStubInstance(NetworkImpl);
        amc = new ContractImpl(network, "amc", "AssetManager");
    });

    afterEach(() => {
        sinon.restore();
    });

    describe("create HTLC for unique asset", () => {
        let amcStub;

        beforeEach(() => {
            amcStub = sinon.stub(amc, "submitTransaction").resolves(false);
        });

        it("asset lock fails with invalid parameters", async () => {
            let expiryTimeSecs = Math.floor(Date.now()/1000) + 300;   // Convert epoch milliseconds to seconds and add 5 minutes
            let assetLockInvocation = await assetManager.createHTLC(null, assetType, assetID, recipientECert, "", "", expiryTimeSecs);
            expect(assetLockInvocation).to.be.an('object').that.has.all.keys('preimage', 'hash', 'result');
            expect(assetLockInvocation.preimage).to.be.a("string");
            expect(assetLockInvocation.preimage.length).to.equal(0);
            expect(assetLockInvocation.hash).to.be.a("string");
            expect(assetLockInvocation.hash.length).to.equal(0);
            expect(assetLockInvocation.result).to.be.a('boolean');
            expect(assetLockInvocation.result).to.equal(false);
            assetLockInvocation = await assetManager.createHTLC(amc, "", assetID, recipientECert, "", "", expiryTimeSecs);
            expect(assetLockInvocation).to.be.an('object').that.has.all.keys('preimage', 'hash', 'result');
            expect(assetLockInvocation.preimage).to.be.a("string");
            expect(assetLockInvocation.preimage.length).to.equal(0);
            expect(assetLockInvocation.hash).to.be.a("string");
            expect(assetLockInvocation.hash.length).to.equal(0);
            expect(assetLockInvocation.result).to.be.a('boolean');
            expect(assetLockInvocation.result).to.equal(false);
            assetLockInvocation = await assetManager.createHTLC(amc, assetType, "", recipientECert, "", "", expiryTimeSecs);
            expect(assetLockInvocation).to.be.an('object').that.has.all.keys('preimage', 'hash', 'result');
            expect(assetLockInvocation.preimage).to.be.a("string");
            expect(assetLockInvocation.preimage.length).to.equal(0);
            expect(assetLockInvocation.hash).to.be.a("string");
            expect(assetLockInvocation.hash.length).to.equal(0);
            expect(assetLockInvocation.result).to.be.a('boolean');
            expect(assetLockInvocation.result).to.equal(false);
            assetLockInvocation = await assetManager.createHTLC(amc, assetType, assetID, "", "", "", expiryTimeSecs);
            expect(assetLockInvocation).to.be.an('object').that.has.all.keys('preimage', 'hash', 'result');
            expect(assetLockInvocation.preimage).to.be.a("string");
            expect(assetLockInvocation.preimage.length).to.equal(0);
            expect(assetLockInvocation.hash).to.be.a("string");
            expect(assetLockInvocation.hash.length).to.equal(0);
            expect(assetLockInvocation.result).to.be.a('boolean');
            expect(assetLockInvocation.result).to.equal(false);
            assetLockInvocation = await assetManager.createHTLC(amc, assetType, assetID, recipientECert, "", "", expiryTimeSecs - 600);     // Expiry time in the past
            expect(assetLockInvocation).to.be.an('object').that.has.all.keys('preimage', 'hash', 'result');
            expect(assetLockInvocation.preimage).to.be.a("string");
            expect(assetLockInvocation.preimage.length).to.equal(0);
            expect(assetLockInvocation.hash).to.be.a("string");
            expect(assetLockInvocation.hash.length).to.equal(0);
            expect(assetLockInvocation.result).to.be.a('boolean');
            expect(assetLockInvocation.result).to.equal(false);
        });

        it("submit asset lock invocation", async () => {
            let assetAgreementStr = assetManager.createAssetExchangeAgreementSerialized(assetType, assetID, recipientECert, "");
            const hashValue = "abcdef123456";
            let expiryTimeSecs = Math.floor(Date.now()/1000) + 300;   // Convert epoch milliseconds to seconds and add 5 minutes
            let lockInfoStr = assetManager.createAssetLockInfoSerialized(hashValue, expiryTimeSecs);
            amcStub.withArgs("LockAsset", assetAgreementStr, lockInfoStr).resolves(true);
            let assetLockInvocation = await assetManager.createHTLC(amc, assetType, assetID, recipientECert, "", hashValue, expiryTimeSecs);
            expect(assetLockInvocation).to.be.an('object').that.has.all.keys('preimage', 'hash', 'result');
            expect(assetLockInvocation.preimage).to.be.a("string");
            expect(assetLockInvocation.preimage.length).to.equal(0);
            expect(assetLockInvocation.hash).to.be.a("string");
            expect(assetLockInvocation.hash).to.equal(hashValue);
            expect(assetLockInvocation.result).to.be.a('boolean');
            expect(assetLockInvocation.result).to.equal(true);
            amcStub.withArgs("LockAsset", assetAgreementStr, sinon.match.any).resolves(true);
            assetLockInvocation = await assetManager.createHTLC(amc, assetType, assetID, recipientECert, "", hashValue, expiryTimeSecs);
            expect(assetLockInvocation).to.be.an('object').that.has.all.keys('preimage', 'hash', 'result');
            expect(assetLockInvocation.preimage).to.be.a("string");
            expect(assetLockInvocation.preimage.length).to.equal(0);
            expect(assetLockInvocation.hash).to.be.a("string");
            expect(assetLockInvocation.hash).to.equal(hashValue);
            expect(assetLockInvocation.result).to.be.a('boolean');
            expect(assetLockInvocation.result).to.equal(true);
            assetLockInvocation = await assetManager.createHTLC(amc, assetType, assetID, recipientECert, "", "", expiryTimeSecs);
            expect(assetLockInvocation).to.be.an('object').that.has.all.keys('preimage', 'hash', 'result');
            expect(assetLockInvocation.preimage).to.be.a("string");
            expect(assetLockInvocation.preimage.length).to.be.above(0);
            expect(assetLockInvocation.hash).to.be.a("string");
            expect(assetLockInvocation.hash.length).to.be.above(0);
            expect(assetLockInvocation.result).to.be.a('boolean');
            expect(assetLockInvocation.result).to.equal(true);
            const hashPreimage = "some-preimage";
            assetLockInvocation = await assetManager.createHTLC(amc, assetType, assetID, recipientECert, hashPreimage, "", expiryTimeSecs);
            expect(assetLockInvocation).to.be.an('object').that.has.all.keys('preimage', 'hash', 'result');
            expect(assetLockInvocation.preimage).to.equal(hashPreimage);
            expect(assetLockInvocation.hash).to.be.a("string");
            expect(assetLockInvocation.hash.length).to.be.above(0);
            expect(assetLockInvocation.result).to.be.a('boolean');
            expect(assetLockInvocation.result).to.equal(true);
            const testAttr = assetType + ':' + assetID + ':' + recipientECert + ':' + hashPreimage + ':' + hashValue;
            const timeoutCb = function(c, t, i, r, p, v) {
                console.log('Asset lock TIMEOUT at', Date());
                c.testAttr = t + ':' + i + ':' + r + ':' + p + ':' + v;
            };
            expiryTimeSecs = Math.floor(Date.now()/1000) + 3;   // 3 seconds
            assetLockInvocation = await assetManager.createHTLC(amc, assetType, assetID, recipientECert, hashPreimage, hashValue, expiryTimeSecs, timeoutCb);
            await sleep(4000);
            expect(amc).to.have.own.property('testAttr');
            expect(amc.testAttr).to.equal(testAttr);
        });
    });

    describe("create HTLC for fungible asset", () => {
        let amcStub;

        beforeEach(() => {
            amcStub = sinon.stub(amc, "submitTransaction").resolves("");
        });

        it("asset lock fails with invalid parameters", async () => {
            let expiryTimeSecs = Math.floor(Date.now()/1000) + 300;   // Convert epoch milliseconds to seconds and add 5 minutes
            let assetLockInvocation = await assetManager.createFungibleHTLC(null, fungibleAssetType, numUnits, recipientECert, "", "", expiryTimeSecs);
            expect(assetLockInvocation).to.be.an('object').that.has.all.keys('preimage', 'hash', 'result');
            expect(assetLockInvocation.preimage).to.be.a("string");
            expect(assetLockInvocation.preimage.length).to.equal(0);
            expect(assetLockInvocation.hash).to.be.a("string");
            expect(assetLockInvocation.hash.length).to.equal(0);
            expect(assetLockInvocation.result).to.be.a('string');
            expect(assetLockInvocation.result).to.equal('');
            assetLockInvocation = await assetManager.createFungibleHTLC(amc, "", numUnits, recipientECert, "", "", expiryTimeSecs);
            expect(assetLockInvocation).to.be.an('object').that.has.all.keys('preimage', 'hash', 'result');
            expect(assetLockInvocation.preimage).to.be.a("string");
            expect(assetLockInvocation.preimage.length).to.equal(0);
            expect(assetLockInvocation.hash).to.be.a("string");
            expect(assetLockInvocation.hash.length).to.equal(0);
            expect(assetLockInvocation.result).to.be.a('string');
            expect(assetLockInvocation.result).to.equal('');
            assetLockInvocation = await assetManager.createFungibleHTLC(amc, fungibleAssetType, -1, recipientECert, "", "", expiryTimeSecs);
            expect(assetLockInvocation).to.be.an('object').that.has.all.keys('preimage', 'hash', 'result');
            expect(assetLockInvocation.preimage).to.be.a("string");
            expect(assetLockInvocation.preimage.length).to.equal(0);
            expect(assetLockInvocation.hash).to.be.a("string");
            expect(assetLockInvocation.hash.length).to.equal(0);
            expect(assetLockInvocation.result).to.be.a('string');
            expect(assetLockInvocation.result).to.equal('');
            assetLockInvocation = await assetManager.createFungibleHTLC(amc, fungibleAssetType, numUnits, "", "", "", expiryTimeSecs);
            expect(assetLockInvocation).to.be.an('object').that.has.all.keys('preimage', 'hash', 'result');
            expect(assetLockInvocation.preimage).to.be.a("string");
            expect(assetLockInvocation.preimage.length).to.equal(0);
            expect(assetLockInvocation.hash).to.be.a("string");
            expect(assetLockInvocation.hash.length).to.equal(0);
            expect(assetLockInvocation.result).to.be.a('string');
            expect(assetLockInvocation.result).to.equal('');
            assetLockInvocation = await assetManager.createFungibleHTLC(amc, fungibleAssetType, numUnits, recipientECert, "", "", expiryTimeSecs - 600);    // Expiry time in the past
            expect(assetLockInvocation).to.be.an('object').that.has.all.keys('preimage', 'hash', 'result');
            expect(assetLockInvocation.preimage).to.be.a("string");
            expect(assetLockInvocation.preimage.length).to.equal(0);
            expect(assetLockInvocation.hash).to.be.a("string");
            expect(assetLockInvocation.hash.length).to.equal(0);
            expect(assetLockInvocation.result).to.be.a('string');
            expect(assetLockInvocation.result).to.equal('');
        });

        it("submit asset lock invocation", async () => {
            let assetAgreementStr = assetManager.createFungibleAssetExchangeAgreementSerialized(fungibleAssetType, numUnits, recipientECert, "");
            const hashValue = "abcdef123456";
            let expiryTimeSecs = Math.floor(Date.now()/1000) + 300;   // Convert epoch milliseconds to seconds and add 5 minutes
            let lockInfoStr = assetManager.createAssetLockInfoSerialized(hashValue, expiryTimeSecs);
            const contractId = "CONTRACT-1234";
            amcStub.withArgs("LockFungibleAsset", assetAgreementStr, lockInfoStr).resolves(contractId);
            let assetLockInvocation = await assetManager.createFungibleHTLC(amc, fungibleAssetType, numUnits, recipientECert, "", hashValue, expiryTimeSecs);
            expect(assetLockInvocation).to.be.an('object').that.has.all.keys('preimage', 'hash', 'result');
            expect(assetLockInvocation.preimage).to.be.a("string");
            expect(assetLockInvocation.preimage.length).to.equal(0);
            expect(assetLockInvocation.hash).to.be.a("string");
            expect(assetLockInvocation.hash).to.equal(hashValue);
            expect(assetLockInvocation.result).to.be.a('string');
            expect(assetLockInvocation.result).to.equal(contractId);
            amcStub.withArgs("LockFungibleAsset", assetAgreementStr, sinon.match.any).resolves(contractId);
            assetLockInvocation = await assetManager.createFungibleHTLC(amc, fungibleAssetType, numUnits, recipientECert, "", hashValue, expiryTimeSecs);
            expect(assetLockInvocation).to.be.an('object').that.has.all.keys('preimage', 'hash', 'result');
            expect(assetLockInvocation.preimage).to.be.a("string");
            expect(assetLockInvocation.preimage.length).to.equal(0);
            expect(assetLockInvocation.hash).to.be.a("string");
            expect(assetLockInvocation.hash).to.equal(hashValue);
            expect(assetLockInvocation.result).to.be.a('string');
            expect(assetLockInvocation.result).to.equal(contractId);
            assetLockInvocation = await assetManager.createFungibleHTLC(amc, fungibleAssetType, numUnits, recipientECert, "", "", expiryTimeSecs);
            expect(assetLockInvocation).to.be.an('object').that.has.all.keys('preimage', 'hash', 'result');
            expect(assetLockInvocation.preimage).to.be.a("string");
            expect(assetLockInvocation.preimage.length).to.be.above(0);
            expect(assetLockInvocation.hash).to.be.a("string");
            expect(assetLockInvocation.hash.length).to.be.above(0);
            expect(assetLockInvocation.result).to.be.a('string');
            expect(assetLockInvocation.result).to.equal(contractId);
            const hashPreimage = "some-preimage";
            assetLockInvocation = await assetManager.createFungibleHTLC(amc, fungibleAssetType, numUnits, recipientECert, hashPreimage, "", expiryTimeSecs);
            expect(assetLockInvocation).to.be.an('object').that.has.all.keys('preimage', 'hash', 'result');
            expect(assetLockInvocation.preimage).to.equal(hashPreimage);
            expect(assetLockInvocation.hash).to.be.a("string");
            expect(assetLockInvocation.hash.length).to.be.above(0);
            expect(assetLockInvocation.result).to.be.a('string');
            expect(assetLockInvocation.result).to.equal(contractId);
            const testAttr = contractId + ':' + fungibleAssetType + ':' + numUnits + ':' + recipientECert + ':' + hashPreimage + ':' + hashValue;
            const timeoutCb = function(c, i, t, n, r, p, v) {
                console.log('Fungible asset lock TIMEOUT at', Date());
                c.testAttr = i + ':' + t + ':' + n + ':' + r + ':' + p + ':' + v;
            };
            expiryTimeSecs = Math.floor(Date.now()/1000) + 3;   // 3 seconds
            assetLockInvocation = await assetManager.createFungibleHTLC(amc, fungibleAssetType, numUnits, recipientECert, hashPreimage, hashValue, expiryTimeSecs, timeoutCb);
            await sleep(4000);
            expect(amc).to.have.own.property('testAttr');
            expect(amc.testAttr).to.equal(testAttr);
        });
    });

    describe("claim unique asset locked in HTLC", () => {
        let amcStub;
        const hashPreimage = "xyz+123-*ty%";

        beforeEach(() => {
            amcStub = sinon.stub(amc, "submitTransaction").resolves(false);
        });

        it("asset claim fails with invalid parameters", async () => {
            let assetClaimInvocation = await assetManager.claimAssetInHTLC(null, assetType, assetID, lockerECert, hashPreimage);
            expect(assetClaimInvocation).to.be.a('boolean');
            expect(assetClaimInvocation).to.equal(false);
            assetClaimInvocation = await assetManager.claimAssetInHTLC(amc, "", assetID, lockerECert, hashPreimage);
            expect(assetClaimInvocation).to.be.a('boolean');
            expect(assetClaimInvocation).to.equal(false);
            assetClaimInvocation = await assetManager.claimAssetInHTLC(amc, assetType, "", lockerECert, hashPreimage);
            expect(assetClaimInvocation).to.be.a('boolean');
            expect(assetClaimInvocation).to.equal(false);
            assetClaimInvocation = await assetManager.claimAssetInHTLC(amc, assetType, assetID, "", hashPreimage);
            expect(assetClaimInvocation).to.be.a('boolean');
            expect(assetClaimInvocation).to.equal(false);
            assetClaimInvocation = await assetManager.claimAssetInHTLC(amc, assetType, assetID, lockerECert, "");
            expect(assetClaimInvocation).to.be.a('boolean');
            expect(assetClaimInvocation).to.equal(false);
        });

        it("submit asset claim invocation", async () => {
            let assetAgreementStr = assetManager.createAssetExchangeAgreementSerialized(assetType, assetID, "", lockerECert);
            let claimInfoStr = assetManager.createAssetClaimInfoSerialized(hashPreimage);
            amcStub.withArgs("ClaimAsset", assetAgreementStr, claimInfoStr).resolves(true);
            let assetClaimInvocation = await assetManager.claimAssetInHTLC(amc, assetType, assetID, lockerECert, hashPreimage);
            expect(assetClaimInvocation).to.be.a('boolean');
            expect(assetClaimInvocation).to.equal(true);
        });
    });

    describe("claim unique asset locked in HTLC using contractId", () => {
        let amcStub;
        const hashPreimage = "xyz+123-*ty%";
        const contractId = "CONTRACT-1234";

        beforeEach(() => {
            amcStub = sinon.stub(amc, "submitTransaction").resolves(false);
        });

        it("asset claim fails with invalid parameters", async () => {
            let assetClaimInvocation = await assetManager.claimAssetInHTLCusingContractId(null, contractId, hashPreimage);
            expect(assetClaimInvocation).to.be.a('boolean');
            expect(assetClaimInvocation).to.equal(false);
            assetClaimInvocation = await assetManager.claimAssetInHTLCusingContractId(amc, "", hashPreimage);
            expect(assetClaimInvocation).to.be.a('boolean');
            expect(assetClaimInvocation).to.equal(false);
            assetClaimInvocation = await assetManager.claimAssetInHTLCusingContractId(amc, contractId, "");
            expect(assetClaimInvocation).to.be.a('boolean');
            expect(assetClaimInvocation).to.equal(false);
        });

        it("submit asset claim invocation", async () => {
            let claimInfoStr = assetManager.createAssetClaimInfoSerialized(hashPreimage);
            amcStub.withArgs("ClaimAssetUsingContractId", contractId, claimInfoStr).resolves(true);
            let assetClaimInvocation = await assetManager.claimAssetInHTLCusingContractId(amc, contractId, hashPreimage);
            expect(assetClaimInvocation).to.be.a('boolean');
            expect(assetClaimInvocation).to.equal(true);
        });
    });

    describe("claim fungible asset locked in HTLC", () => {
        let amcStub;
        const hashPreimage = "xyz+123-*ty%";
        const contractId = "CONTRACT-1234";

        beforeEach(() => {
            amcStub = sinon.stub(amc, "submitTransaction").resolves(false);
        });

        it("asset claim fails with invalid parameters", async () => {
            let assetClaimInvocation = await assetManager.claimFungibleAssetInHTLC(null, contractId, hashPreimage);
            expect(assetClaimInvocation).to.be.a('boolean');
            expect(assetClaimInvocation).to.equal(false);
            assetClaimInvocation = await assetManager.claimFungibleAssetInHTLC(amc, "", hashPreimage);
            expect(assetClaimInvocation).to.be.a('boolean');
            expect(assetClaimInvocation).to.equal(false);
            assetClaimInvocation = await assetManager.claimFungibleAssetInHTLC(amc, contractId, "");
            expect(assetClaimInvocation).to.be.a('boolean');
            expect(assetClaimInvocation).to.equal(false);
        });

        it("submit asset claim invocation", async () => {
            let claimInfoStr = assetManager.createAssetClaimInfoSerialized(hashPreimage);
            amcStub.withArgs("ClaimFungibleAsset", contractId, claimInfoStr).resolves(true);
            let assetClaimInvocation = await assetManager.claimFungibleAssetInHTLC(amc, contractId, hashPreimage);
            expect(assetClaimInvocation).to.be.a('boolean');
            expect(assetClaimInvocation).to.equal(true);
        });
    });

    describe("reclaim unique asset locked in HTLC", () => {
        let amcStub;

        beforeEach(() => {
            amcStub = sinon.stub(amc, "submitTransaction").resolves(false);
        });

        it("asset reclaim fails with invalid parameters", async () => {
            let assetReclaimInvocation = await assetManager.reclaimAssetInHTLC(null, assetType, assetID, recipientECert);
            expect(assetReclaimInvocation).to.be.a('boolean');
            expect(assetReclaimInvocation).to.equal(false);
            assetReclaimInvocation = await assetManager.reclaimAssetInHTLC(amc, "", assetID, recipientECert);
            expect(assetReclaimInvocation).to.be.a('boolean');
            expect(assetReclaimInvocation).to.equal(false);
            assetReclaimInvocation = await assetManager.reclaimAssetInHTLC(amc, assetType, "", recipientECert);
            expect(assetReclaimInvocation).to.be.a('boolean');
            expect(assetReclaimInvocation).to.equal(false);
            assetReclaimInvocation = await assetManager.reclaimAssetInHTLC(amc, assetType, assetID, "");
            expect(assetReclaimInvocation).to.be.a('boolean');
            expect(assetReclaimInvocation).to.equal(false);
        });

        it("submit asset reclaim invocation", async () => {
            let assetAgreementStr = assetManager.createAssetExchangeAgreementSerialized(assetType, assetID, recipientECert, "");
            amcStub.withArgs("UnlockAsset", assetAgreementStr).resolves(true);
            let assetReclaimInvocation = await assetManager.reclaimAssetInHTLC(amc, assetType, assetID, recipientECert);
            expect(assetReclaimInvocation).to.be.a('boolean');
            expect(assetReclaimInvocation).to.equal(true);
        });
    });

    describe("reclaim unique asset locked in HTLC using contractId", () => {
        let amcStub;
        const contractId = "CONTRACT-1234";

        beforeEach(() => {
            amcStub = sinon.stub(amc, "submitTransaction").resolves(false);
        });

        it("asset reclaim fails with invalid parameters", async () => {
            let assetReclaimInvocation = await assetManager.reclaimAssetInHTLCusingContractId(null, contractId);
            expect(assetReclaimInvocation).to.be.a('boolean');
            expect(assetReclaimInvocation).to.equal(false);
            assetReclaimInvocation = await assetManager.reclaimAssetInHTLCusingContractId(amc, "");
            expect(assetReclaimInvocation).to.be.a('boolean');
            expect(assetReclaimInvocation).to.equal(false);
        });

        it("submit asset reclaim invocation", async () => {
            amcStub.withArgs("UnlockAssetUsingContractId", contractId).resolves(true);
            let assetReclaimInvocation = await assetManager.reclaimAssetInHTLCusingContractId(amc, contractId);
            expect(assetReclaimInvocation).to.be.a('boolean');
            expect(assetReclaimInvocation).to.equal(true);
        });
    });

    describe("reclaim fungible asset locked in HTLC", () => {
        let amcStub;
        const contractId = "CONTRACT-1234";

        beforeEach(() => {
            amcStub = sinon.stub(amc, "submitTransaction").resolves(false);
        });

        it("asset reclaim fails with invalid parameters", async () => {
            let assetReclaimInvocation = await assetManager.reclaimFungibleAssetInHTLC(null, contractId);
            expect(assetReclaimInvocation).to.be.a('boolean');
            expect(assetReclaimInvocation).to.equal(false);
            assetReclaimInvocation = await assetManager.reclaimFungibleAssetInHTLC(amc, "");
            expect(assetReclaimInvocation).to.be.a('boolean');
            expect(assetReclaimInvocation).to.equal(false);
        });

        it("submit asset reclaim invocation", async () => {
            amcStub.withArgs("UnlockFungibleAsset", contractId).resolves(true);
            let assetReclaimInvocation = await assetManager.reclaimFungibleAssetInHTLC(amc, contractId);
            expect(assetReclaimInvocation).to.be.a('boolean');
            expect(assetReclaimInvocation).to.equal(true);
        });
    });

    describe("check unique asset lock status in HTLC", () => {
        let amcStub;

        beforeEach(() => {
            amcStub = sinon.stub(amc, "evaluateTransaction").resolves(false);
        });

        it("asset lock status check fails with invalid parameters", async () => {
            let assetLockQuery = await assetManager.isAssetLockedInHTLC(null, assetType, assetID, recipientECert, lockerECert);
            expect(assetLockQuery).to.be.a('boolean');
            expect(assetLockQuery).to.equal(false);
            assetLockQuery = await assetManager.isAssetLockedInHTLC(amc, "", assetID, recipientECert, lockerECert);
            expect(assetLockQuery).to.be.a('boolean');
            expect(assetLockQuery).to.equal(false);
            assetLockQuery = await assetManager.isAssetLockedInHTLC(amc, assetType, "", recipientECert, lockerECert);
            expect(assetLockQuery).to.be.a('boolean');
            expect(assetLockQuery).to.equal(false);
            assetLockQuery = await assetManager.isAssetLockedInHTLC(amc, assetType, assetID, "", lockerECert);
            expect(assetLockQuery).to.be.a('boolean');
            expect(assetLockQuery).to.equal(false);
            assetLockQuery = await assetManager.isAssetLockedInHTLC(amc, assetType, assetID, recipientECert, "");
            expect(assetLockQuery).to.be.a('boolean');
            expect(assetLockQuery).to.equal(false);
        });

        it("submit asset lock status query", async () => {
            let assetAgreementStr = assetManager.createAssetExchangeAgreementSerialized(assetType, assetID, recipientECert, lockerECert);
            amcStub.withArgs("IsAssetLocked", assetAgreementStr).resolves(true);
            let assetLockQuery = await assetManager.isAssetLockedInHTLC(amc, assetType, assetID, recipientECert, lockerECert);
            expect(assetLockQuery).to.be.a('boolean');
            expect(assetLockQuery).to.equal(true);
        });
    });

    describe("check unique asset lock status in HTLC using contractId", () => {
        let amcStub;
        const contractId = "CONTRACT-1234";

        beforeEach(() => {
            amcStub = sinon.stub(amc, "evaluateTransaction").resolves(false);
        });

        it("asset lock status check fails with invalid parameters", async () => {
            let assetLockQuery = await assetManager.isAssetLockedInHTLCqueryUsingContractId(null, contractId);
            expect(assetLockQuery).to.be.a('boolean');
            expect(assetLockQuery).to.equal(false);
            assetLockQuery = await assetManager.isAssetLockedInHTLCqueryUsingContractId(amc, null);
            expect(assetLockQuery).to.be.a('boolean');
            expect(assetLockQuery).to.equal(false);
        });

        it("submit asset lock status query", async () => {
            amcStub.withArgs("IsAssetLockedQueryUsingContractId", contractId).resolves(true);
            let assetLockQuery = await assetManager.isAssetLockedInHTLCqueryUsingContractId(amc, contractId);
            expect(assetLockQuery).to.be.a('boolean');
            expect(assetLockQuery).to.equal(true);
        });
    });

    describe("check fungible asset lock status in HTLC", () => {
        let amcStub;
        const contractId = "CONTRACT-1234";

        beforeEach(() => {
            amcStub = sinon.stub(amc, "evaluateTransaction").resolves(false);
        });

        it("asset lock status check fails with invalid parameters", async () => {
            let assetLockQuery = await assetManager.isFungibleAssetLockedInHTLC(null, contractId);
            expect(assetLockQuery).to.be.a('boolean');
            expect(assetLockQuery).to.equal(false);
            assetLockQuery = await assetManager.isFungibleAssetLockedInHTLC(amc, "");
            expect(assetLockQuery).to.be.a('boolean');
            expect(assetLockQuery).to.equal(false);
        });

        it("submit asset lock status query", async () => {
            amcStub.withArgs("IsFungibleAssetLocked", contractId).resolves(true);
            let assetLockQuery = await assetManager.isFungibleAssetLockedInHTLC(amc, contractId);
            expect(assetLockQuery).to.be.a('boolean');
            expect(assetLockQuery).to.equal(true);
        });
    });
});
