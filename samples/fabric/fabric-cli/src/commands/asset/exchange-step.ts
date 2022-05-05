/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

import { GluegunCommand } from 'gluegun'
import { fabricHelper } from '../../helpers/fabric-functions'
import logger from '../../helpers/logger'
import { commandHelp, getNetworkConfig, handlePromise } from '../../helpers/helpers'
import { AssetManager } from '@hyperledger-labs/weaver-fabric-interop-sdk'

var crypto = require('crypto')

const command: GluegunCommand = {
  name: 'exchange-step',
  alias: ['-es'],
  description: 'Asset Exchange Step by Step.',
  run: async toolbox => {
    const {
      print,
      parameters: { options, array }
    } = toolbox
    if (options.help || options.h) {
      commandHelp(
        print,
        toolbox,
        `fabric-cli asset exchange-step --step=1 --target-network=network1 --secret=secrettext --timeout-duration=100 --locker=bob --recipient=alice --param=Type1:a04`,
        'fabric-cli asset exchange-step --step=<1-8> --target-network=<network1|network2> --secret=<secret> --hash=<hashvalue> --timeout-epoch=<timeout-epoch> --timeout-duration=<timeout-duration> --locker=<locker-userid> --recipient=<recipient-userid> --contract-id=<contract-id> --param=<param>',
        [
          {
            name: '--step',
            description:
              'Step number of asset exchange protocol: \t\t\t\t\t\n1: LockAsset \t\t\t\t\t\n2: IsAssetLocked \n3: LockFungibleAsset \n4: IsFungibleAssetLocked \n5: ClaimFungibleAsset \n6: ClaimAsset\n7: UnlockAsset\n8: UnlockFungibleAsset'
          },
          {
            name: '--target-network',
            description:
              'Target network for command. <network1|network2>'
          },
          {
            name: '--secret',
            description:
              'secret text to be used by Asset owner to hash lock. (Required for step 5, 6)'
          },
          {
            name: '--hash',
            description:
              'hash value in base64 to be used for HTLC. (use only one of secret or hash, do not use both options)'
          },
          {
            name: '--timeout-epoch',
            description:
              'Timeout in epoch in seconds. Use only one of the timeout options. (Only required for Step 1,3)'
          },
          {
            name: '--timeout-duration',
            description:
              'Timeout duration in seconds. Use only one of the timeout options. (Only required for Step 1,3)'
          },
          {
            name: '--locker',
            description:
              'Locker User Id: Must be already registered in target-network (Required for All steps)'
          },
          {
            name: '--recipient',
            description:
              'Recipient User Id: Must be already registered in target-network (Required for All steps)'
          },
          {
            name: '--contract-id',
            description:
              'Contract Id: Required for step 4,5 i.e. IsFungibleAssetLocked/ClaimFungibleAsset'
          },
          {
            name: '--param',
            description:
              'Param: AssetType:AssetId for Non-Fungible Assets \nFungibleAssetType:NumUnits for Fungible Assets \n(Required for steps 1-3)'
          },
          {
            name: '--shared',
            description:
              'represents coowned/shared asset'
          },
          {
            name: '--debug',
            description:
              'Shows debug logs when running. Disabled by default. To enable --debug=true'
          }
        ],
        command,
        ['asset', 'exchange-step']
      )
      return
    }
    if (options.debug === 'true') {
      logger.level = 'debug'
      logger.debug('Debugging is enabled')
    }
    if (!options['step']) {
      print.error('Step number not provided.')
      return
    }
    if (!options['target-network'])
    {
      print.error('--target-network needs to specified')
      return
    }
    if (!options['locker'] || !options['recipient'])
    {
      print.error('Locker and Recipient both needs to be specified')
      return
    }

    // Locker and Recipient
    const locker = options['locker']
    const recipient = options['recipient']

    // Hash Pre-image
    let secret = ''
    let hash_secret = ''
    if (options['secret'])
    {
      secret = options['secret']
      hash_secret = crypto.createHash('sha256').update(secret).digest('base64');
    }
    else if (options['hash'])
    {
      hash_secret = options['hash']
    }

    // Timeout
    var timeout=0, timeout2=0;
    const currTime = Math.floor(Date.now()/1000);
    if (options['timeout-epoch']) {
      let duration = options['timeout-epoch'] - currTime
      timeout = options['timeout-epoch']
      timeout2 = options['timeout-epoch'] + duration
    }
    else if (options['timeout-duration']) {
      timeout=currTime + options['timeout-duration']
      timeout2=currTime + 2 * options['timeout-duration']
    }

    let param1, param2
    if (options['param']) {
      const params = options['param'].split(':')
      param1 = params[0]
      param2 = params[1]
    }

    const netConfig = getNetworkConfig(options['target-network'])
    if (!netConfig.connProfilePath || !netConfig.channelName || !netConfig.chaincode) {
      print.error(
        `Please use a valid --target-network. No valid environment found for ${options['target-network']} `
      )
      return
    }

    let coOwnerCerts, networkL
    if (options['shared'] && options['shared']=='true') {
      const coOwners = locker.split(',')
      coOwnerCerts = coOwners.map(element => element);
      for (var sz = 0; sz < coOwners.length; sz++) {
        networkL = await fabricHelper({
          channel: netConfig.channelName,
          contractName: netConfig.chaincode,
          connProfilePath: netConfig.connProfilePath,
          networkName: options['target-network'],
          mspId: netConfig.mspId,
          userString: coOwners[sz]
        })
        var coOwner = await networkL.wallet.get(coOwners[sz])
        coOwnerCerts[sz] = Buffer.from((coOwner).credentials.certificate).toString('base64')
      }
    }

    let newCoOwnerCerts, networkR
    if (options['shared'] && options['shared']=='true') {
      const newCoOwners = recipient.split(',')
      newCoOwnerCerts = newCoOwners.map(element => element);
      for (var sz = 0; sz < newCoOwners.length; sz++) {
        networkR = await fabricHelper({
          channel: netConfig.channelName,
          contractName: netConfig.chaincode,
          connProfilePath: netConfig.connProfilePath,
          networkName: options['target-network'],
          mspId: netConfig.mspId,
          userString: newCoOwners[sz]
        })
        var newCoOwner = await networkR.wallet.get(newCoOwners[sz])
        newCoOwnerCerts[sz] = Buffer.from((newCoOwner).credentials.certificate).toString('base64')
      }
    }

    let lockerId, lockerCert
    if (!options['shared'] || options['shared'] != 'true') {
        networkL = await fabricHelper({
          channel: netConfig.channelName,
          contractName: netConfig.chaincode,
          connProfilePath: netConfig.connProfilePath,
          networkName: options['target-network'],
          mspId: netConfig.mspId,
          userString: locker
        })
        lockerId = await networkL.wallet.get(locker)
        lockerCert = Buffer.from((lockerId).credentials.certificate).toString('base64')
    }

    let recipientId, recipientCert
    if (!options['shared'] || options['shared'] != 'true') {
        networkR = await fabricHelper({
          channel: netConfig.channelName,
          contractName: netConfig.chaincode,
          connProfilePath: netConfig.connProfilePath,
          networkName: options['target-network'],
          mspId: netConfig.mspId,
          userString: recipient
        })
        recipientId = await networkR.wallet.get(recipient)
        recipientCert = Buffer.from((recipientId).credentials.certificate).toString('base64')
    }

    const spinner = print.spin(`Asset Exchange:\n`)

    var res

    if (options['step']===true) {
        spinner.fail('Asset Exchange: Invalid Step number. Exiting.')
    }
    else if (options['step']=='1') {
      if (!options['param']) {
        print.error(
          `Please provide a asset type and id in --param`
        )
        spinner.fail(`Error`)
      } else if (options['shared'] && options['shared']=='true') {
        try {
          spinner.info(`Trying Shared Asset Lock: ${param1}, ${param2} by ${locker} for ${recipient}`)
          var timeTaken="Lock Asset Time"
          console.time(timeTaken)
          res = await AssetManager.createSharedHTLC(networkL.contract,
                          param1,
                          param2,
			  coOwnerCerts.join(','),
                          newCoOwnerCerts.join(','),
                          secret,
                          hash_secret,
                          timeout2,
                          null)
          console.timeEnd(timeTaken)
          if (!res.result) {
            throw new Error()
          }
          spinner.info(`Shared Asset Locked: ${res.result}, preimage: ${res.preimage}, hashvalue: ${hash_secret}`)
          spinner.succeed('Shared Asset Exchange: Step 1 Complete.')
        } catch(error) {
            print.error(`Could not Lock Shared Asset in ${options['target-network']} ${error}`)
            spinner.fail(`Error`)
        }
      }
      else {
        try {
          spinner.info(`Trying Asset Lock: ${param1}, ${param2} by ${locker} for ${recipient}`)
          res = await AssetManager.createHTLC(networkL.contract,
                          param1,
                          param2,
                          recipientCert,
                          secret,
                          hash_secret,
                          timeout2,
                          null)
          if (!res.result) {
            throw new Error()
          }
          spinner.info(`Asset Locked: ${res.result}, preimage: ${res.preimage}, hashvalue: ${hash_secret}`)
          spinner.succeed('Asset Exchange: Step 1 Complete.')
        } catch(error) {
            print.error(`Could not Lock Asset in ${options['target-network']}`)
            spinner.fail(`Error`)
        }
      }
    }

    else if (options['step']=='2') {
      if (!options['param']) {
        print.error(
          `Please provide a asset type and id in --param`
        )
        spinner.fail(`Error`)
      } else if (options['shared'] && options['shared']=='true') {
        try {
          spinner.info(`Testing if shared asset is locked: ${param1}, ${param2} by ${locker} for ${recipient}`)
          var timeTaken="IsLock Asset Time"
          console.time(timeTaken)
          res = await AssetManager.isSharedAssetLockedInHTLC(networkR.contract,
                          param1,
                          param2,
                          newCoOwnerCerts.join(','),
			  coOwnerCerts.join(','))
          console.timeEnd(timeTaken)
          spinner.info(`Is Shared Asset Locked Return: ${res}`)
          spinner.succeed('Shared Asset Exchange: Step 2 Complete.')
        } catch(error) {
            print.error(`Could not call isSharedAssetLockedInHTLC in ${options['target-network']}`)
            spinner.fail(`Error`)
        }
      }
      else {
        try {
          spinner.info(`Testing if asset is locked: ${param1}, ${param2} by ${locker} for ${recipient}`)
          res = await AssetManager.isAssetLockedInHTLC(networkR.contract,
                          param1,
                          param2,
                          recipientCert,
                          lockerCert)
          spinner.info(`Is Asset Locked Return: ${res}`)
          spinner.succeed('Asset Exchange: Step 2 Complete.')
        } catch(error) {
            print.error(`Could not call isAssetLockedInHTLC in ${options['target-network']}`)
            spinner.fail(`Error`)
        }
      }
    }

    else if (options['step']=='3') {
      if (!options['param']) {
        print.error(
          `Please provide a fungible asset type and num of units in --param`
        )
        spinner.fail(`Error`)
      }
      else if (options['secret'] || !options['hash']) {
        print.error(
          `Please provide hash value, do not specify preimage for this step.`
        )
        spinner.fail(`Error`)
      }
      else {
        try {
          spinner.info(`Trying Fungible Asset Lock: ${param1}, ${param2} by ${locker} for ${recipient}`)
          res = await AssetManager.createFungibleHTLC(networkL.contract,
                          param1,
                          param2,
                          recipientCert,
                          '',
                          hash_secret,
                          timeout,
                          null)
          if (!res.result) {
            throw new Error()
          }
          spinner.info(`Fungible Asset Locked, ContractId: ${res.result}, preimage: ${res.preimage}`)
          spinner.succeed('Asset Exchange: Step 3 Complete.')
        } catch(error) {
            print.error(`Could not Lock Fungible Asset in ${options['target-network']}`)
            spinner.fail(`Error`)
        }
      }
    }

    else if (options['step']=='4') {
      if (!options['contract-id']) {
        print.error(
          `Please provide the contract id`
        )
        spinner.fail(`Error`)
      }
      else {
        const contractId = options['contract-id']
        try {
          spinner.info(`Testing if fungible asset is locked: ${contractId}`)
          res = await AssetManager.isFungibleAssetLockedInHTLC(networkR.contract,
                          contractId)
          spinner.info(`Is Fungible Asset Locked Return: ${res}`)
          spinner.succeed('Asset Exchange: Step 4 Complete.')
        } catch(error) {
            print.error(`Could not call isFungibleAssetLockedInHTLC in ${options['target-network']}`)
            spinner.fail(`Error`)
        }
      }
    }

    else if (options['step']=='5') {
      if (!options['secret']) {
        print.error(
          `Please provide the preimage in --secret`
        )
        spinner.fail(`Error`)
      }
      else if (!options['contract-id']) {
        print.error(
          `Please provide the contract id`
        )
        spinner.fail(`Error`)
      }
      else {
        const contractId = options['contract-id']
        try {
          spinner.info(`Trying Fungible Asset Claim: ${contractId}`)
          res = await AssetManager.claimFungibleAssetInHTLC(networkR.contract,
                          contractId,
                          secret)
          if (!res) {
            throw new Error()
          }
          spinner.info(`Fungible Asset Claimed: ${res}`)
          spinner.succeed('Asset Exchange: Step 5 Complete.')
        } catch(error) {
            print.error(`Could not claim fungible asset in ${options['target-network']}`)
            spinner.fail(`Error`)
        }
      }
    }

    else if (options['step']=='6') {
      if (!options['secret']) {
        print.error(
          `Please provide the preimage in --secret`
        )
        spinner.fail(`Error`)
      } else if (options['shared'] && options['shared']=='true') {
        try {
          spinner.info(`Trying Shared Asset Claim: ${param1} ${param2}`)
          var timeTaken="Claim Asset Time"
          console.time(timeTaken)
          res = await AssetManager.claimSharedAssetInHTLC(networkR.contract,
                          param1,
                          param2,
                          coOwnerCerts.join(','),
			  newCoOwnerCerts.join(','),
                          secret)
          console.timeEnd(timeTaken)
          if (!res) {
            throw new Error()
          }
          spinner.info(`Shared Asset Claimed: ${res}`)
          spinner.succeed('Shared Asset Exchange: All Steps Complete.')
        } catch(error) {
            print.error(`Could not claim shared asset in ${options['target-network']}`)
            spinner.fail(`Error`)
        }
      }
      else {
        try {
          spinner.info(`Trying Asset Claim: ${param1} ${param2}`)
          res = await AssetManager.claimAssetInHTLC(networkR.contract,
                          param1,
                          param2,
                          lockerCert,
                          secret)
          if (!res) {
            throw new Error()
          }
          spinner.info(`Asset Claimed: ${res}`)
          spinner.succeed('Asset Exchange: All Steps Complete.')
        } catch(error) {
            print.error(`Could not claim asset in ${options['target-network']}`)
            spinner.fail(`Error`)
        }
      }
    }
    else if (options['step']=='7') {
      if (!options['param']) {
        print.error(
          `Please provide a asset type and id in --param`
        )
        spinner.fail(`Error`)
      } else if (options['shared'] && options['shared']=='true') {
        try {
          spinner.info(`Trying Shared Asset Unlock: ${param1} ${param2}`)
          var timeTaken="ReClaim Asset Time"
          console.time(timeTaken)
          res = await AssetManager.reclaimSharedAssetInHTLC(networkL.contract,
                                      param1,
                                      param2,
				      coOwnerCerts.join(','),
                                      newCoOwnerCerts.join(','));
          console.timeEnd(timeTaken)
          if (!res) {
            throw new Error()
          }
          spinner.succeed(`Shared Asset Reclaimed: ${res}`)
        } catch(error) {
            print.error(`Could not claim shared asset in ${options['target-network']}`)
            spinner.fail(`Error`)
        }
      }
      else {
        try {
          spinner.info(`Trying Asset Unlock: ${param1} ${param2}`)
          res = await AssetManager.reclaimAssetInHTLC(networkL.contract,
                                      param1,
                                      param2,
                                      recipientCert);
          if (!res) {
            throw new Error()
          }
          spinner.succeed(`Asset Reclaimed: ${res}`)
        } catch(error) {
            print.error(`Could not claim asset in ${options['target-network']}`)
            spinner.fail(`Error`)
        }
      }
    }
    else if (options['step']=='8') {
      if (!options['contract-id']) {
        print.error(
          `Please provide the contract id`
        )
        spinner.fail(`Error`)
      }
      else {
        const contractId = options['contract-id']
        try {
          spinner.info(`Trying Fungible Asset Unlock, contractId: ${contractId}`)
          res = await AssetManager.reclaimFungibleAssetInHTLC(networkL.contract,
                                        contractId);
          if (!res) {
            throw new Error()
          }
          spinner.succeed(`Fungible Asset Reclaimed: ${res}`)
        } catch(error) {
            print.error(`Could not claim asset in ${options['target-network']}`)
            spinner.fail(`Error`)
        }
      }
    }
    else {
      spinner.fail('Asset Exchange: Invalid Step number. Exiting.')
    }

    await networkL.gateway.disconnect()
    await networkR.gateway.disconnect()

    console.log('Gateways disconnected.')

    process.exit()
  }
}


module.exports = command
