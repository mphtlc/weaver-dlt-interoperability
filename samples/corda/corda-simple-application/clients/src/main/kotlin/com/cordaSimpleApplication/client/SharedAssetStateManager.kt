/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.cordaSimpleApplication.client

import com.cordaSimpleApplication.flow.IssueSharedAssetState
import com.cordaSimpleApplication.flow.GetSharedAssetStatesByType
import com.cordaSimpleApplication.flow.IssueSharedAssetStateFromStateRef
import com.cordaSimpleApplication.flow.DeleteSharedAssetState
import com.cordaSimpleApplication.flow.GetSharedAssetStateByLinearId
import com.cordaSimpleApplication.flow.RetrieveSharedAssetStateAndRef
import com.cordaSimpleApplication.flow.MergeSharedAssetStates
import com.cordaSimpleApplication.flow.SplitSharedAssetState

import com.cordaSimpleApplication.flow.TransferSharedAssetStateInitiator
import com.cordaSimpleApplication.state.SharedAssetState
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.default
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.identity.CordaX500Name
import java.lang.Exception
import net.corda.core.identity.Party
import net.corda.core.messaging.startFlow

class SharedAssetCommand : CliktCommand(name = "shared-fungible", help ="Manages shared fungible asset life cycle (e.g., issue, get, delete)") {
    override fun run() {
    }
}

/**
 * The CLI command used to trigger a IssueSharedAssetState flow.
 *
 * @property quantity The number of units of the shared fungible asset with state [SharedAssetState].
 * @property assetType The type of the shared fungible asset with state [SharedAssetState].
 */
class IssueSharedAssetStateCommand : CliktCommand(name = "issue-asset", help = "Invokes the IssueSharedAssetState flow. Requires quantity, type and co-owners of the asset.") {
    private val quantity: String by argument()
    private val assetType: String by argument()
    private val coOwnerString: String? by option("-co", "--co-owners", help="Names of parities that co-own the shared asset")
    val config by requireObject<Map<String, String>>()
    override fun run() {
        if (coOwnerString == null) {
            println("Arguments required: --co-owners")
        }

        issueSharedAssetStateHelper(quantity.toLong(), assetType, coOwnerString!!, config)
    }
}

/**
 * Helper function used by IssueSharedAssetStateCommand
 */
fun issueSharedAssetStateHelper(quantity: Long, assetType: String, coOwnerString: String, config: Map<String, String>) {
    val rpc = NodeRPCConnection(
            host = config["CORDA_HOST"]!!,
            username = "clientUser1",
            password = "test",
            rpcPort = config["CORDA_PORT"]!!.toInt())
    try {
        val proxy = rpc.proxy
        val coOwners: List<Party> = getCoOwnersFromString(coOwnerString, proxy)
        println("IssueSharedAssetState flow with arguments quantity: $quantity, type: $assetType, and co-owners: $coOwners")

        val createdState = proxy.startFlow(::IssueSharedAssetState, quantity, assetType, coOwners)
                .returnValue.get().tx.outputStates.first() as SharedAssetState
        println(createdState)
    } catch (e: Exception) {
        println(e.toString())
    } finally {
        rpc.close()
    }
}

fun getCoOwnersFromString(coownerstring: String, proxy: CordaRPCOps) : List<Party> {
    val coOwners = coownerstring.split(";").toTypedArray();
    var members = listOf<Party>()
    coOwners.forEach {
        members += proxy.wellKnownPartyFromX500Name(CordaX500Name.parse(it))!!
    }

    return members
}

class IssueSharedAssetStateFromStateRefCommand : CliktCommand(name = "issue-asset-from-state-ref", help = "Invokes the IssueSharedAssetStateFromStateRef flow. Requires a linearId and co-owners.") {
    private val linearId: String by argument()
    private val coOwnerString: String? by option("-co", "--co-owners", help="Names of parities that co-own the shared asset")
    val config by requireObject<Map<String, String>>()
    override fun run() {
        val rpc = NodeRPCConnection(
            host = config["CORDA_HOST"]!!,
            username = "clientUser1",
            password = "test",
            rpcPort = config["CORDA_PORT"]!!.toInt())
        try {
            if (coOwnerString == null) {
                println("Arguments required: --co-owners")
            }
            
            val proxy: CordaRPCOps = rpc.proxy
            val coOwners: List<Party> = getCoOwnersFromString(coOwnerString!!, proxy)
            println("IssueSharedAssetStateFromStateRef flow with arguments $linearId and $coOwners")

            val createdState = proxy.startFlow(::IssueSharedAssetStateFromStateRef, linearId, coOwners)
                .returnValue.get().tx.outputStates.first() as SharedAssetState
            println(createdState)
        } catch (e: Exception) {
            println(e.toString())
        } finally {
            rpc.close()
        }
    }
}

/**
 * The CLI command used to trigger a GetSharedAssetStatesByType flow.
 *
 * @property assetType The filter criteria for the [SharedAssetState]s to be retrieved.
 */
class GetSharedAssetStatesByTypeCommand : CliktCommand(name = "get-assets-by-type", help = "Get shared fungible asset states by type. Requires a fungible asset type") {
    private val assetType: String by argument()
    val config by requireObject<Map<String, String>>()
    override fun run() {
        println("Get fungible asset states with type $assetType")
        val rpc = NodeRPCConnection(
            host = config["CORDA_HOST"]!!,
            username = "clientUser1",
            password = "test",
            rpcPort = config["CORDA_PORT"]!!.toInt())
        try {
            val proxy = rpc.proxy
            val states = proxy.startFlow(::GetSharedAssetStatesByType, assetType)
                .returnValue.get()
            println(states.toString(Charsets.UTF_8))
        } catch (e: Exception) {
            println(e.toString())
        } finally {
            rpc.close()
        }
    }
}

/**
 * The CLI command used to trigger a GetSharedAssetStateByLinearId flow.
 *
 * @property linearId The linearId for the [SharedAssetState] to be retrieved.
 */
class GetSharedAssetStateByLinearIdCommand : CliktCommand(name = "get-asset-by-linear-id", help = "Gets shared fungible asset state by linearId. Requires a linearId") {
    private val linearId: String by argument()
    val config by requireObject<Map<String, String>>()
    override fun run() {
        println("Get shared fungible asset state with linearId $linearId")
        val rpc = NodeRPCConnection(
            host = config["CORDA_HOST"]!!,
            username = "clientUser1",
            password = "test",
            rpcPort = config["CORDA_PORT"]!!.toInt())
        try {
            val proxy = rpc.proxy
            val state = proxy.startFlow(::GetSharedAssetStateByLinearId, linearId)
                .returnValue.get()
            println(state)
        } catch (e: Exception) {
            println(e.toString())
        } finally {
            rpc.close()
        }
    }
}

/**
 * The CLI command used to trigger a DeleteSharedAssetState flow.
 *
 * @property linearId The filter for the [SharedAssetState] to be deleted.
 */
class DeleteSharedAssetStateCommand : CliktCommand(name = "delete-asset", help = "Invokes the DeleteSharedAssetState flow. Requires a linearId") {
    private val linearId: String by argument()
    val config by requireObject<Map<String, String>>()
    override fun run() {
        println("DeleteSharedAssetState flow with linearId $linearId")
        val rpc = NodeRPCConnection(
            host = config["CORDA_HOST"]!!,
            username = "clientUser1",
            password = "test",
            rpcPort = config["CORDA_PORT"]!!.toInt())
        try {
            val proxy = rpc.proxy
            val deletedState = proxy.startFlow(::DeleteSharedAssetState, linearId)
                .returnValue.get().inputs.first()
            println(deletedState)
        } catch (e: Exception) {
            println(e.toString())
        } finally {
            rpc.close()
        }
    }
}

/**
 * The CLI command used to trigger a RetrieveSharedAssetStateAndRef flow.
 *
 * @property type The type of the shared fungible asset [SharedAssetState] to be retrieved.
 * @property quantity The number of units of the shared fungible asset in [SharedAssetState] to be retrieved.
 */
class RetrieveSharedAssetStateAndRefCommand : CliktCommand(name = "retrieve-state-and-ref", help = "Invokes the RetrieveStateAndRef flow. Requires the shared fungible asset type and quantity") {
    val assetType: String by argument()
    val quantity: String by argument()
    val config by requireObject<Map<String, String>>()
    override fun run() {
        println("RetrieveSharedAssetStateAndRef flow with asset type: $assetType and quantity: $quantity")
        val rpc = NodeRPCConnection(
            host = config["CORDA_HOST"]!!,
            username = "clientUser1",
            password = "test",
            rpcPort = config["CORDA_PORT"]!!.toInt())
        try {
            val proxy = rpc.proxy
            val stateAndRef = proxy.startFlow(::RetrieveSharedAssetStateAndRef, assetType, quantity.toLong())
                .returnValue.get()
            println(stateAndRef.toString())
        } catch (e: Exception) {
            println(e.toString())
        } finally {
            rpc.close()
        }
    }
}

/**
 * The CLI command used to trigger a MergeSharedAssetStates flow.
 *
 * @property linearId1 The filter for the first shared fungible asset [SharedAssetState] used in the merge operation.
 * @property linearId2 The filter for the second shared fungible asset [SharedAssetState] used in the merge operation.
 */
class MergeSharedAssetStatesCommand : CliktCommand(name = "merge-asset-states", help = "Invokes the MergeAssetStates flow. Requires two linearIds") {
    private val linearId1: String by argument()
    private val linearId2: String by argument()
    val config by requireObject<Map<String, String>>()
    override fun run() {
        println("MergeSharedAssetStates flow with linearIds $linearId1 and $linearId2")
        val rpc = NodeRPCConnection(
            host = config["CORDA_HOST"]!!,
            username = "clientUser1",
            password = "test",
            rpcPort = config["CORDA_PORT"]!!.toInt())
        try {
            val proxy = rpc.proxy
            val mergedState = proxy.startFlow(::MergeSharedAssetStates, linearId1, linearId2)
                .returnValue.get().inputs.first()
            println(mergedState)
        } catch (e: Exception) {
            println(e.toString())
        } finally {
            rpc.close()
        }
    }
}

/**
 * The CLI command used to trigger a SplitSharedAssetState flow.
 *
 * @property linearId The filter for the [AssetState] to be split.
 * @property quantity1 The number of units of shared fungible asset in the first [SharedAssetState] created after split.
 * @property quantity2 The number of units of shared fungible asset in the second [SharedAssetState] created after split.
 */
class SplitSharedAssetStateCommand : CliktCommand(name = "split-asset", help = "Invokes the SplitSharedAssetState flow. Requires a linearId") {
    private val linearId: String by argument()
    private val quantity1: String by argument()
    private val quantity2: String by argument()
    val config by requireObject<Map<String, String>>()
    override fun run() {
        println("SplitSharedAssetState flow with linearId $linearId")
        val rpc = NodeRPCConnection(
            host = config["CORDA_HOST"]!!,
            username = "clientUser1",
            password = "test",
            rpcPort = config["CORDA_PORT"]!!.toInt())
        try {
            val proxy = rpc.proxy
            val outputStates = proxy.startFlow(::SplitSharedAssetState, linearId, quantity1.toLong(), quantity2.toLong())
                .returnValue.get().inputs
            println(outputStates)
        } catch (e: Exception) {
            println(e.toString())
        } finally {
            rpc.close()
        }
    }
}

/**
 * The CLI command used to trigger a TransferSharedAssetStateInitiator flow.
 *
 * @property linearId The filter for the [SharedAssetState] to be transferred.
 * @property updatedCoOwnerString The parties who will co-own the asset with state [SharedAssetState] will be transferred to
 */
class TransferSharedAssetStateCommand : CliktCommand(name = "transfer-asset", help = "Invokes the TransferAssetState flow. Requires a linearId and updated coOwners") {
    private val linearId: String by argument()
    private val updatedCoOwnerString: String? by option("-uco", "--updated-co-owners", help="Names of parities that will co-own the shared fungible asset")
    val config by requireObject<Map<String, String>>()
    override fun run() {
        println("Initiate 'TransferSharedAssetState' flow with linearId $linearId")
        val rpc = NodeRPCConnection(
            host = config["CORDA_HOST"]!!,
            username = "clientUser1",
            password = "test",
            rpcPort = config["CORDA_PORT"]!!.toInt())
        try {
            if (updatedCoOwnerString == null) {
                println("Arguments required: --updated-co-owners")
            }
            val proxy = rpc.proxy
            val updatedCoOwners: List<Party> = getCoOwnersFromString(updatedCoOwnerString!!, proxy)
            val outputStates = proxy.startFlow(::TransferSharedAssetStateInitiator, linearId, updatedCoOwners)
                .returnValue.get().inputs
            println(outputStates)
        } catch (e: Exception) {
            println(e.toString())
        } finally {
            rpc.close()
        }
    }
}