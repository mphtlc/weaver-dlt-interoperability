/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.cordaSimpleApplication.contract

import com.cordaSimpleApplication.state.SharedAssetState
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction
import java.security.PublicKey

/**
 * An implementation of a sample shared fungible asset in Corda.
 *
 * This contract enforces rules regarding the creation of a valid [SharedAssetState], and operations on [SharedAssetState].
 *
 * For a new [SharedAssetState] to be issued onto the ledger, a transaction is required which takes:
 * - Zero input states.
 * - One output state: the new [SharedAssetState].
 * - An Issue() command with the public keys of the co-owners of the shared asset.
 *
 */
class SharedAssetContract : Contract {
    companion object {
        @JvmStatic
        val ID = "com.cordaSimpleApplication.contract.SharedAssetContract"
    }

    /**
     * The verify() function of all the states' contracts must not throw an exception for a transaction to be
     * considered valid.
     */
    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<SharedAssetContract.Commands>()
        when (command.value) {
            is SharedAssetContract.Commands.Issue -> requireThat {
                // Generic constraints around the shared fungible asset issuance transaction.
                "No inputs should be consumed when issuing an asset." using (tx.inputsOfType<SharedAssetState>().isEmpty())
                "Only one output state should be created." using (tx.outputsOfType<SharedAssetState>().size == 1)
                val outputState = tx.outputsOfType<SharedAssetState>().single()
                val requiredSigners = outputState.participants.map { it.owningKey }
                "The participants must be the signers." using (command.signers.containsAll(requiredSigners))
            }
            is SharedAssetContract.Commands.Delete -> requireThat {
                // Generic constraints around the shared fungible asset deletion transaction
                "Only one input state should be consumed with deletion of an asset." using (tx.inputsOfType<SharedAssetState>().size == 1)
                "No output state should be created." using (tx.outputsOfType<SharedAssetState>().isEmpty())
                val inputState = tx.inputsOfType<SharedAssetState>()[0]
                var requiredSigners = listOf<PublicKey>()
                for (owner in inputState.coOwners) {
                    requiredSigners += owner.owningKey
                }
                "The asset owner must be the signer." using (command.signers.containsAll(requiredSigners))
            }
            is SharedAssetContract.Commands.Merge -> requireThat {
                // Generic constraints around the transaction that merges two asset states into one
                "Two input states should be consumed for merging." using (tx.inputsOfType<SharedAssetState>().size == 2)
                val inputState1 = tx.inputsOfType<SharedAssetState>()[0]
                val inputState2 = tx.inputsOfType<SharedAssetState>()[1]
                "Both shared assets to be merged should belong to the same set of co-owners." using (inputState1.coOwners.toSet() == inputState2.coOwners.toSet())
                "Both shared assets to be merged should be of same fungible asset type." using (inputState1.type == inputState2.type)
                "Only one output state should be created." using (tx.outputsOfType<SharedAssetState>().size == 1)
                val mergedState = tx.outputsOfType<SharedAssetState>().single()
                val requiredSigners = mergedState.participants.map { it.owningKey }
                "The participants must be the signers." using (command.signers.containsAll(requiredSigners))
                "The output state should belong to the same set of co-owners as the input states." using (inputState1.coOwners.toSet() == mergedState.coOwners.toSet())
                "The number of units of the shared fungible asset before and after merge should be same." using (inputState1.quantity + inputState2.quantity == mergedState.quantity)
                "The merged shared fungible asset type should be same as the input shared fungible asset type." using (inputState1.type == mergedState.type)
            }
            is SharedAssetContract.Commands.Split -> requireThat {
                // Generic constraints around the transaction that splits an asset state into two asset states
                "One input state should be consumed for splitting." using (tx.inputsOfType<SharedAssetState>().size == 1)
                val splitState = tx.inputsOfType<SharedAssetState>()[0]
                "Two output states should be created." using (tx.outputsOfType<SharedAssetState>().size == 2)
                val outputState1 = tx.outputsOfType<SharedAssetState>()[0]
                val outputState2 = tx.outputsOfType<SharedAssetState>()[1]
                "Both shared fungible assets generated by split should belong to the same owner." using (outputState1.coOwners.toSet() == outputState2.coOwners.toSet())
                "Both shared fungible assets generated by split should be of the same type." using (outputState1.type == outputState2.type)
                val requiredSigners = outputState1.participants.map { it.owningKey }
                "The participants must be the signers." using (command.signers.containsAll(requiredSigners))
                "The output states should belong to the same set of co-owners as the input states." using (splitState.coOwners.toSet() == outputState1.coOwners.toSet())
                "The number of units of the shared fungible asset before and after split should be same." using (splitState.quantity == outputState1.quantity + outputState2.quantity)
                "The shared fungible asset type to be split should be same as the output shared fungible assets' type." using (splitState.type == outputState1.type)
            }
            is SharedAssetContract.Commands.Transfer -> requireThat {
                // Generic constraints around the transaction that transfers ownership of a shared asset from a group of co-owning Parties to a different group of co-owning Parties
                // (there can be one or more Parties common in both the before and after the ownership transfer of the shared asset)
                "One input state should be consumed for transferring." using (tx.inputsOfType<SharedAssetState>().size == 1)
                val inputState = tx.inputsOfType<SharedAssetState>()[0]
                "One output state only should be created." using (tx.outputsOfType<SharedAssetState>().size == 1)
                val outputState = tx.outputsOfType<SharedAssetState>()[0]
                "The input and output states part of the transfer should have the same quantity." using (inputState.quantity == outputState.quantity)
                "The input and output states part of the transfer should be of same token type." using (inputState.type == outputState.type)
                "Transfer from a set of co-owners to the same set of co-owners is not possible." using (inputState.coOwners.toSet() != outputState.coOwners.toSet())
                var requiredSigners = listOf<PublicKey>()
                for (owner in inputState.coOwners) {
                    requiredSigners += owner.owningKey
                }
                for (owner in outputState.coOwners) {
                    requiredSigners += owner.owningKey
                }
                "The co-owners of the input and output assets must be the signers." using (command.signers.containsAll(requiredSigners))
            }
        }
    }

    /**
     * This contract implements the commands: Issue, Delete, Merge, Split and Transfer.
     */
    interface Commands : CommandData {
        class Issue : Commands
        class Delete : Commands
        class Merge : Commands
        class Split : Commands
        class Transfer : Commands
    }
}
