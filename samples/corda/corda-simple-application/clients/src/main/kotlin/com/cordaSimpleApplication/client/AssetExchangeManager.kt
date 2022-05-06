/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.cordaSimpleApplication.client

import kotlin.system.*
import arrow.core.Either
import arrow.core.Left
import arrow.core.Right
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.default
import java.io.File
import java.lang.Exception
import kotlinx.coroutines.runBlocking
import net.corda.core.messaging.startFlow
import com.google.protobuf.util.JsonFormat
import java.util.Base64
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.crypto.sha256
import net.corda.core.identity.Party
import java.time.Instant
import net.corda.core.identity.CordaX500Name

import com.weaver.corda.sdk.AssetManager
import com.cordaSimpleApplication.state.AssetState
import com.cordaSimpleApplication.state.SharedAssetState
import com.cordaSimpleApplication.contract.AssetContract
import com.cordaSimpleApplication.contract.SharedAssetContract

/**
 * Generates Hash to be used for HTLC for a given secret:
 * get-hash -s <secret>
 * get-hash --secret=<secret>
 */
class GetHashCommand : CliktCommand(
        help = "Generates Hash to be used for HTLC for a given secret. Usage: get-hash --secret=<secret>") {
    val secret by option("-s", "--secret", help="String to be hashed")
    override fun run() = runBlocking {
        if (secret == null) {
            println("Parameter --secret or -s not given.")
        } else {
            val secret = OpaqueBytes(secret!!.toByteArray())
            val hashBase64 = Base64.getEncoder().encodeToString(secret.sha256().bytes)
            println("Hash in Base64: ${hashBase64}")
        }
    }
}

/**
 * Command to lock an asset.
 * lock-asset --hash=hash --timeout=timeout --recipient="O=PartyB,L=London,C=GB" --param=type:id ----> non-fungible
 * lock-asset --fungible --hash=hash --timeout=timeout --recipient="O=PartyB,L=London,C=GB" --param=type:amount ----> fungible
 */
class LockAssetCommand : CliktCommand(
        help = "Locks an asset. lock-asset --fungible --hashBase64=hashbase64 --timeout=10 --recipient='PartyA' --param=type:amount ") {
    val config by requireObject<Map<String, String>>()
    val hashBase64: String? by option("-h64", "--hashBase64", help="Hash in base64 for HTLC")
    val timeout: String? by option("-t", "--timeout", help="Timeout duration in seconds.")
    val recipient: String? by option("-r", "--recipient", help="Party Name for recipient")
    val shared: Boolean by option("-s", "--shared", help="Shared Asset Pledge: True/False").flag(default = false)
    val coOwnerString: String? by option("-cos", "--co-owners", help="Names of the co-owners locking the shared asset")
    val recipientCoOwnerString: String? by option("-rcos", "--recipient-co-owners", help="Names of the shared asset recipient co-owners")
    val fungible: Boolean by option("-f", "--fungible", help="Fungible Asset Lock: True/False").flag(default = false)
    val param: String? by option("-p", "--param", help="Parameter AssetType:AssetId for non-fungible, AssetType:Quantity for fungible.")
    override fun run() = runBlocking {
        if (!shared && recipient == null) {
            println("Arguement -r (name of the asset recipient) is required")
        } else if (shared && coOwnerString == null) {
            println("Arguement -cos (names of the shared asset co-owners) is required")
        } else if (shared && recipientCoOwnerString == null) {
            println("Arguement -rcos (names of the recipient co-owners of the shared asset) is required")
        } else if (param == null) {
            println("Arguement -p (asset details to be pledged) is required")
        } else if (hashBase64 == null) {
            println("Arguement -h64 (hash in base64 for HTLC) is required")
        } else {
            var nTimeout: Long
            if (timeout == null) {
                nTimeout = 10L
            } else {
                nTimeout = timeout!!.toLong()
            }
            val rpc = NodeRPCConnection(
                    host = config["CORDA_HOST"]!!,
                    username = "clientUser1",
                    password = "test",
                    rpcPort = config["CORDA_PORT"]!!.toInt())
            try {
                val params = param!!.split(":").toTypedArray()
                var id: Any? = null
                val issuer = rpc.proxy.wellKnownPartyFromX500Name(CordaX500Name.parse("O=PartyA,L=London,C=GB"))!!
                if (!shared) {
                    var observers: List<Party> = listOf<Party>()
                    var coOwners: List<Party> = listOf<Party>()
                    val thisParty: Party = rpc.proxy.nodeInfo().legalIdentities.get(0)
                    coOwners += thisParty
                    if (fungible) {
                        id = AssetManager.createFungibleHTLC(
                            rpc.proxy,
                            params[0],          // Type
                            params[1].toLong(), // Quantity
                            recipient!!,
                            hashBase64!!,
                            nTimeout,
                            1,                  // nTimeout represents Duration
                            "com.cordaSimpleApplication.flow.RetrieveStateAndRef",
                            AssetContract.Commands.Delete(),
                            issuer,
                            observers,
                            coOwners
                        )
                    } else {
                        id = AssetManager.createHTLC(
                            rpc.proxy,
                            params[0],      // Type
                            params[1],      // ID
                            recipient!!,
                            hashBase64!!,
                            nTimeout,
                            1,              // nTimeout represents Duration
                            "com.cordaSimpleApplication.flow.RetrieveStateAndRef",
                            AssetContract.Commands.Delete(),
                            issuer,
                            observers,
                            coOwners
                        )
                    }
                } else {
                    // Obtain the co-owners locking the shared asset, who need to provide their signatures
                    var coOwners: List<Party> = listOf<Party>()
                    val coOwnerNames: Array<String> = coOwnerString!!.split(";").toTypedArray();
                    coOwnerNames.forEach {
                        coOwners += rpc.proxy.wellKnownPartyFromX500Name(CordaX500Name.parse(it))!!
                    }

                    // Obtain the co-owners who are recipients of the shared asset, who need to provide their signatures
                    var recipientCoOwners: List<Party> = listOf<Party>()
                    val recipientCoOwnerNames: Array<String> = recipientCoOwnerString!!.split(";").toTypedArray();
                    recipientCoOwnerNames.forEach {
                        recipientCoOwners += rpc.proxy.wellKnownPartyFromX500Name(CordaX500Name.parse(it))!!
                    }

                    var observers: List<Party> = listOf<Party>()

                    if (fungible) {
                        val elapsed = measureTimeMillis {
                            id = AssetManager.createFungibleHTLC(
                                rpc.proxy,
                                params[0],          // Type
                                params[1].toLong(), // Quantity
                                recipientCoOwners.joinToString(";"),
                                hashBase64!!,
                                nTimeout,
                                1,                  // nTimeout represents Duration
                                "com.cordaSimpleApplication.flow.RetrieveSharedAssetStateAndRef",
                                SharedAssetContract.Commands.Delete(),
                                issuer,
                                observers,
                                coOwners
                            )
                        }
                        println("Lock Asset Time: ${elapsed}")
                    }
                }
                println("HTLC Lock State created with contract ID ${id}.")
            } catch (e: Exception) {
              println("Error: ${e.toString()}")
            } finally {
                rpc.close()
            }
        }
    }
}

/**
 * Command to claim a locked asset.
 */
class ClaimAssetCommand : CliktCommand(help = "Claim a locked asset. Only Recipient's call will work.") {
    val config by requireObject<Map<String, String>>()
    val contractId: String? by option("-cid", "--contract-id", help="Contract/Linear Id for HTLC State")
    val shared: Boolean by option("-co", "--shared", help="Shared Asset Claim: True/False").flag(default = false)
    val secret: String? by option("-s", "--secret", help="Hash Pre-Image for the HTLC Claim")
    override fun run() = runBlocking {
        if (contractId == null || secret == null) {
            println("Arguments required: --contract-id and --secret.")
        } else {
            val rpc = NodeRPCConnection(
                    host = config["CORDA_HOST"]!!,
                    username = "clientUser1",
                    password = "test",
                    rpcPort = config["CORDA_PORT"]!!.toInt())
            try {
                val issuer = rpc.proxy.wellKnownPartyFromX500Name(CordaX500Name.parse("O=PartyA,L=London,C=GB"))!!
                var res: Any = ""
                if (!shared) {
                    res = AssetManager.claimAssetInHTLC(
                        rpc.proxy,
                        contractId!!,
                        secret!!,
                        AssetContract.Commands.Issue(),
                        "com.cordaSimpleApplication.flow.UpdateAssetOwnerFromPointer",
                        issuer
                    )
                } else {
                    val timeInMillis = measureTimeMillis {
                        res = AssetManager.claimAssetInHTLC(
                            rpc.proxy,
                            contractId!!,
                            secret!!,
                            SharedAssetContract.Commands.Issue(),
                            "com.cordaSimpleApplication.flow.UpdateSharedAssetCoOwnersFromPointer",
                            issuer
                        )
                    }
                    println("Claim Time Taken: $timeInMillis")
                }
                println("Asset Claim Response: ${res}")
            } catch (e: Exception) {
              println("Error: ${e.toString()}")
            } finally {
                rpc.close()
            }
        }
    }
}

/**
 * Command to unlock a locked asset after timeout as per HTLC.
 */
class UnlockAssetCommand : CliktCommand(help = "Unlocks a locked asset after timeout. Only lockers's call will work.") {
    val config by requireObject<Map<String, String>>()
    val contractId: String? by option("-cid", "--contract-id", help="Contract/Linear Id for HTLC State")
    val shared: Boolean by option("-s", "--shared", help="Shared Asset Unlock: True/False").flag(default = false)
    override fun run() = runBlocking {
        if (contractId == null) {
            println("Arguments required: --contract-id.")
        } else {
            val rpc = NodeRPCConnection(
                    host = config["CORDA_HOST"]!!,
                    username = "clientUser1",
                    password = "test",
                    rpcPort = config["CORDA_PORT"]!!.toInt())
            try {
                val issuer = rpc.proxy.wellKnownPartyFromX500Name(CordaX500Name.parse("O=PartyA,L=London,C=GB"))!!
                var res: Any = ""
                if (!shared) {
                    res = AssetManager.reclaimAssetInHTLC(
                        rpc.proxy,
                        contractId!!,
                        AssetContract.Commands.Issue(),
                        issuer
                    )
                } else {
                    val timeInMillis = measureTimeMillis {
                        res = AssetManager.reclaimAssetInHTLC(
                            rpc.proxy,
                            contractId!!,
                            SharedAssetContract.Commands.Issue(),
                            issuer
                        )
                    }
                    println("Unlock Time Taken: ${timeInMillis}")
                }
                println("Asset Unlock Response: ${res}")
            } catch (e: Exception) {
              println("Error: ${e.toString()}")
            } finally {
                rpc.close()
            }
        }
    }
}

/**
 * Query lock status of an asset.
 */
class IsAssetLockedCommand : CliktCommand(help = "Query lock status of an asset, given contractId.") {
    val config by requireObject<Map<String, String>>()
    val contractId: String? by option("-cid", "--contract-id", help="Contract/Linear Id for HTLC State")
    override fun run() = runBlocking {
        if (contractId == null) {
            println("Arguments required: --contract-id.")
        } else {        
            val rpc = NodeRPCConnection(
                    host = config["CORDA_HOST"]!!,
                    username = "clientUser1",
                    password = "test",
                    rpcPort = config["CORDA_PORT"]!!.toInt())
            try {
                var res: Any = ""
                val timeInMillis = measureTimeMillis {
                    res = AssetManager.isAssetLockedInHTLC(
                        rpc.proxy, 
                        contractId!!
                    )
                }
                println("Is Asset Lock Time: ${timeInMillis}")
                println("Is Asset Locked Response: ${res}")
            } catch (e: Exception) {
              println("Error: ${e.toString()}")
            } finally {
                rpc.close()
            }
        }
    }
}
/**
 * Fetch HTLC State associated with contractId.
 */
class GetLockStateCommand : CliktCommand(help = "Fetch HTLC State associated with contractId.") {
    val config by requireObject<Map<String, String>>()
    val contractId: String? by option("-cid", "--contract-id", help="Contract/Linear Id for HTLC State")
    override fun run() = runBlocking {
        if (contractId == null) {
            println("Arguments required: --contract-id.")
        } else {
            val rpc = NodeRPCConnection(
                    host = config["CORDA_HOST"]!!,
                    username = "clientUser1",
                    password = "test",
                    rpcPort = config["CORDA_PORT"]!!.toInt())
            try {
                val res = AssetManager.readHTLCStateByContractId(
                    rpc.proxy, 
                    contractId!!
                )
                println("Response: ${res}")
            } catch (e: Exception) {
              println("Error: ${e.toString()}")
            } finally {
                rpc.close()
            }
        }
    }
}

/**
 * Query lock hash of an HTLC.
 */
class GetHTLCHashCommand : CliktCommand(name = "get-htlc-hash",
    help = "Query lock hash of a locked asset using HTLC, given contractId.")
{
    val config by requireObject<Map<String, String>>()
    val contractId: String? by option("-cid", "--contract-id", help="Contract/Linear Id for HTLC State")
    override fun run() = runBlocking {
        if (contractId == null) {
            println("Arguments required: --contract-id.")
        } else {        
            val rpc = NodeRPCConnection(
                    host = config["CORDA_HOST"]!!,
                    username = "clientUser1",
                    password = "test",
                    rpcPort = config["CORDA_PORT"]!!.toInt())
            try {
                val res = AssetManager.getHTLCHashById(
                    rpc.proxy, 
                    contractId!!
                )
                println("GetHTLCHash Response: ${res}")
            } catch (e: Exception) {
              println("Error: ${e.toString()}")
            } finally {
                rpc.close()
            }
        }
    }
}

/**
 * Query claim hash preimage of an HTLC.
 */
class GetHTLCPreImageCommand : CliktCommand(name = "get-htlc-preimage",
    help = "Query hash preimage of a claimed asset using HTLC, given contractId.")
{
    val config by requireObject<Map<String, String>>()
    val contractId: String? by option("-cid", "--contract-id", help="Contract/Linear Id for HTLC State")
    override fun run() = runBlocking {
        if (contractId == null) {
            println("Arguments required: --contract-id.")
        } else {        
            val rpc = NodeRPCConnection(
                    host = config["CORDA_HOST"]!!,
                    username = "clientUser1",
                    password = "test",
                    rpcPort = config["CORDA_PORT"]!!.toInt())
            try {
                val res = AssetManager.getHTLCHashPreImageById(
                    rpc.proxy, 
                    contractId!!
                )
                println("GetHTLCPreImage Response: ${res}")
            } catch (e: Exception) {
              println("Error: ${e.toString()}")
            } finally {
                rpc.close()
            }
        }
    }
}
