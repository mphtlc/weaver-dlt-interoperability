/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.cordaSimpleApplication.flow

import co.paralleluniverse.fibers.Suspendable
import com.cordaSimpleApplication.state.SharedAssetState
import com.cordaSimpleApplication.state.SharedAssetStateJSON
import com.cordaSimpleApplication.contract.SharedAssetContract
import javassist.NotFoundException
import net.corda.core.contracts.Command
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.Party
import net.corda.core.contracts.StaticPointer
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.node.ServiceHub
import net.corda.core.flows.*
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.ProgressTracker.Step
import net.corda.core.contracts.requireThat
import sun.security.x509.UniqueIdentity
import java.util.*
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.weaver.corda.app.interop.flows.GetAssetClaimStatusState
import com.weaver.corda.app.interop.states.AssetPledgeState
import com.weaver.corda.app.interop.flows.GetAssetPledgeStatus
import com.weaver.corda.app.interop.flows.AssetPledgeStateToProtoBytes
import java.security.PublicKey


/**
 * The IssueSharedAssetState flow creates a fungible asset shared/co-owned by a set/group of parties.
 * @property quantity the number of units of the shared fungible asset with the state [SharedAssetState] to be issued.
 * @property type the type of the shared fungible asset with the state [SharedAssetState] to be issued.
 */
@InitiatingFlow
@StartableByRPC
class IssueSharedAssetState(val quantity: Long, val type: String, val coOwners: List<Party>) : FlowLogic<SignedTransaction>() {
    /**
     * The progress tracker checkpoints each stage of the flow and outputs the specified messages when each
     * checkpoint is reached in the code. See the 'progressTracker.currentStep' expressions within the call() function.
     */
    companion object {
        object GENERATING_TRANSACTION : Step("Generating transaction based on new shared fungible asset state.")
        object VERIFYING_TRANSACTION : Step("Verifying contract constraints.")
        object SIGNING_TRANSACTION : Step("Signing transaction with our private key.")
        object GATHERING_SIGS : Step("Gathering the signatures of the co-owners of the shared asset.") {
            override fun childProgressTracker() = CollectSignaturesFlow.tracker()
        }
        object FINALISING_TRANSACTION : Step("Obtaining notary signature and recording transaction.") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }

        fun tracker() = ProgressTracker(
                GENERATING_TRANSACTION,
                VERIFYING_TRANSACTION,
                SIGNING_TRANSACTION,
                GATHERING_SIGS,
                FINALISING_TRANSACTION
        )
    }

    override val progressTracker = tracker()

    /**
     * The flow logic is encapsulated within the call() method.
     */
    @Suspendable
    override fun call(): SignedTransaction {

        // Obtain a reference from a notary we wish to use.
        val notary = serviceHub.networkMapCache.notaryIdentities.single()

        // Stage 1.
        progressTracker.currentStep = GENERATING_TRANSACTION
        // Generate an unsigned transaction.
        val assetState: SharedAssetState = SharedAssetState(quantity, type, coOwners)

        val commandData: SharedAssetContract.Commands.Issue = SharedAssetContract.Commands.Issue()
        val txCommand: Command<SharedAssetContract.Commands.Issue> = Command(commandData, assetState.participants.map { it.owningKey })
        val txBuilder: TransactionBuilder = TransactionBuilder(notary)
                .addOutputState(assetState, SharedAssetContract.ID)
                .addCommand(txCommand)

        // Stage 2.
        progressTracker.currentStep = VERIFYING_TRANSACTION
        // Verify that the transaction is valid.
        txBuilder.verify(serviceHub)

        // Stage 3.
        progressTracker.currentStep = SIGNING_TRANSACTION
        // Sign the transaction.
        val partSignedTx: SignedTransaction = serviceHub.signInitialTransaction(txBuilder)
        println("Transaction submitter signed transaction.")

        // Stage 4.
        progressTracker.currentStep = GATHERING_SIGS
        // Gather signatures from the co-owners on the transaction.
        var sessions: List<FlowSession> = listOf<FlowSession>()
        var isTxSubmittedByCoOwner: Boolean = false
        for (member in coOwners) {
            if (!member.equals(ourIdentity)) {
                val membersession = initiateFlow(member)
                sessions += membersession
            } else {
                // transaction should be submitted by one of the co-owners only
                isTxSubmittedByCoOwner = true
            }
        }
        if (!isTxSubmittedByCoOwner) {
            println("Transaction submitter ($ourIdentity) has to be one of the co-owners")
            throw NotFoundException("Transaction submitter ($ourIdentity) has to be one of the co-owners")
        }
        val fullySignedTx: SignedTransaction = subFlow(CollectSignaturesFlow(partSignedTx, sessions, GATHERING_SIGS.childProgressTracker()))

        // Stage 5.
        progressTracker.currentStep = FINALISING_TRANSACTION
        // Notarise and record the transaction in all parties' vaults.
        return subFlow(FinalityFlow(fullySignedTx, sessions, FINALISING_TRANSACTION.childProgressTracker()))
    }
}

@InitiatedBy(IssueSharedAssetState::class)
class IssueAcceptor(val session: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val signTransactionFlow = object : SignTransactionFlow(session) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat {
            }
        }
        try {
            val txId = subFlow(signTransactionFlow).id
            println("Co-owner ($ourIdentity) signed issue shared asset transaction.")
            return subFlow(ReceiveFinalityFlow(session, expectedTxId = txId))
        } catch (e: Exception) {
            println("Error signing issue shared asset transaction by co-owner ($ourIdentity): ${e.message}\n")
            return subFlow(ReceiveFinalityFlow(session))
        }
    }
}

/*
 * The UpdateSharedAssetCoOwnersFromPointer flow only updates the coOwners field of the [SharedAssetState] pointed by Static Pointer 
 * argument. This flow doesn't create any transaction or update any state in vault.
 * @property inputStatePointer [StaticPointer<SharedAssetState>]
 * @property updatedCoOwners updated list of coOwners of the shared fungible asset
 */
@InitiatingFlow
@StartableByRPC
class UpdateSharedAssetCoOwnersFromPointer(
    private val inputStatePointer: StaticPointer<SharedAssetState>,
    private val updatedCoOwners: List<Party>
) : FlowLogic<SharedAssetState>() {
    /*
     * @Returns SharedAssetState
     */
    override fun call(): SharedAssetState {

        var isTxSubmittedByCoOwner: Boolean = false
        for (member in updatedCoOwners) {
            if (member.equals(ourIdentity)) {
                // transaction should be submitted by one of the co-owners only
                isTxSubmittedByCoOwner = true
            }
        }
        if (!isTxSubmittedByCoOwner) {
            println("Transaction submitter ($ourIdentity) has to be one of the co-owners")
            throw NotFoundException("Transaction submitter ($ourIdentity) has to be one of the co-owners")
        }

        val inputState: SharedAssetState = inputStatePointer.resolve(serviceHub).state.data
        // return SharedAssetState(inputState.quantity, inputState.type, updatedCoOwners)
        // below creates an asset state with the same linearId
        return inputState.copy(coOwners= updatedCoOwners)
    }
}

/**
 * The DeleteSharedAssetState flow is used to delete an existing [SharedAssetState].
 *
 * @property linearId the filter for the [SharedAssetState] to be deleted.
 */
@InitiatingFlow
@StartableByRPC
class DeleteSharedAssetState(val linearId: String) : FlowLogic<SignedTransaction>() {
    /**
     * The progress tracker checkpoints each stage of the flow and outputs the specified messages when each
     * checkpoint is reached in the code. See the 'progressTracker.currentStep' expressions within the call() function.
     */
    companion object {
        object GENERATING_TRANSACTION : Step("Generating transaction based on the passed linearId.")
        object VERIFYING_TRANSACTION : Step("Verifying contract constraints.")
        object SIGNING_TRANSACTION : Step("Signing transaction with our private key.")
        object GATHERING_SIGS : Step("Gathering the signatures of the co-owners of the shared asset.") {
            override fun childProgressTracker() = CollectSignaturesFlow.tracker()
        }
        object FINALISING_TRANSACTION : Step("Obtaining notary signature and recording transaction.") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }
        fun tracker() = ProgressTracker(
            GENERATING_TRANSACTION,
            VERIFYING_TRANSACTION,
            SIGNING_TRANSACTION,
            GATHERING_SIGS,
            FINALISING_TRANSACTION
        )
    }

    override val progressTracker = tracker()

    /**
     * The call() method captures the logic to build and sign a transaction that deletes an [AssetState].
     *
     * @return returns the signed transaction.
     */
    @Suspendable
    override fun call(): SignedTransaction {
        // Obtain a reference to the notary we want to use.
        val notary = serviceHub.networkMapCache.notaryIdentities[0]

        // Stage 1.
        progressTracker.currentStep = GENERATING_TRANSACTION
        // Generate an unsigned transaction.
        val uuid = UniqueIdentifier.Companion.fromString(linearId)
        val criteria = QueryCriteria.LinearStateQueryCriteria(null, Arrays.asList(uuid),
            Vault.StateStatus.UNCONSUMED, null)
        val assetStatesWithLinearId = serviceHub.vaultService.queryBy<SharedAssetState>(criteria).states

        if (assetStatesWithLinearId.isEmpty()) {
            throw NotFoundException("SharedAssetState with linearId $linearId not found")
        }
        val inputStateAndRef: StateAndRef<SharedAssetState> = assetStatesWithLinearId.first()
        println("Deleting asset state from the ledger: $inputStateAndRef\n")

        val inputState: SharedAssetState = assetStatesWithLinearId.first().state.data
        var requiredSigners = listOf<PublicKey>()
        for (owner in inputState.coOwners) {
            requiredSigners += owner.owningKey
        }
        val txCommand = Command(SharedAssetContract.Commands.Delete(), requiredSigners)
        val txBuilder = TransactionBuilder(notary)
            .addInputState(inputStateAndRef)
            .addCommand(txCommand)

        // Stage 2.
        progressTracker.currentStep = VERIFYING_TRANSACTION
        // Verify that the transaction is valid.
        txBuilder.verify(serviceHub)

        // Stage 3.
        progressTracker.currentStep = SIGNING_TRANSACTION
        // Sign the transaction.
        val partSignedTx: SignedTransaction = serviceHub.signInitialTransaction(txBuilder)
        println("Transaction submitter signed transaction.")

        // Stage 4.
        progressTracker.currentStep = GATHERING_SIGS
        // Gather signatures from the co-owners on the transaction.
        var sessions = listOf<FlowSession>()
        var isTxSubmittedByCoOwner: Boolean = false
        for (member in inputState.coOwners) {
            if (!member.equals(ourIdentity)) {
                val membersession = initiateFlow(member)
                sessions += membersession
            } else {
                // transaction should be submitted by one of the co-owners only
                isTxSubmittedByCoOwner = true
            }
        }
        if (!isTxSubmittedByCoOwner) {
            println("Transaction submitter ($ourIdentity) has to be one of the co-owners")
            throw NotFoundException("Transaction submitter ($ourIdentity) has to be one of the co-owners")
        }
        val fullySignedTx: SignedTransaction = subFlow(CollectSignaturesFlow(partSignedTx, sessions, GATHERING_SIGS.childProgressTracker()))

        // Stage 5.
        progressTracker.currentStep = FINALISING_TRANSACTION
        // Notarise and record the transaction in all parties' vaults.
        return subFlow(FinalityFlow(fullySignedTx, sessions, FINALISING_TRANSACTION.childProgressTracker()))

    }
}

@InitiatedBy(DeleteSharedAssetState::class)
class DeleteAcceptor(val session: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val signTransactionFlow = object : SignTransactionFlow(session) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat {
            }
        }
        try {
            val txId = subFlow(signTransactionFlow).id
            println("Co-owner ($ourIdentity) signed delete shared asset transaction.")
            return subFlow(ReceiveFinalityFlow(session, expectedTxId = txId))
        } catch (e: Exception) {
            println("Error signing delete shared asset transaction by co-owner ($ourIdentity): ${e.message}\n")
            return subFlow(ReceiveFinalityFlow(session))
        }
    }
}

/**
 * The GetSharedAssetStatesByType flow is used to retrieve list of [SharedAssetState]s from the vault based on the asset type.
 *
 * @property assetType the filter for the [SharedAssetState] list to be retrieved.
 */
@StartableByRPC
class GetSharedAssetStatesByType(val assetType: String) : FlowLogic<ByteArray>() {
    @Suspendable

    /**
     * The call() method captures the logic to find one or more [SharedAssetState]s in the vault based on the assetType.
     *
     * @return returns list of [SharedAssetState]s.
     */
    override fun call(): ByteArray {
        val states = serviceHub.vaultService.queryBy<SharedAssetState>().states
            .filter { it.state.data.type == assetType }
            .map { it.state.data }
        println("Retrieved states with assetType $assetType: $states\n")
        return states.toString().toByteArray()
    }
}

/**
 * The GetSharedAssetStateByLinearId flow is used to retrieve a [SharedAssetState] from the vault based on its linearId.
 *
 * @property linearId the linearId for the [SharedAssetState] to be retrieved.
 */
@StartableByRPC
class GetSharedAssetStateByLinearId(val linearId: String) : FlowLogic<String>() {
    @Suspendable

    /**
     * The call() method captures the logic to find a [SharedAssetState] in the vault based on its linearId.
     *
     * @return returns the [SharedAssetState].
     */
    override fun call(): String {
        val uuid = UniqueIdentifier.Companion.fromString(linearId)
        val criteria = QueryCriteria.LinearStateQueryCriteria(null, Arrays.asList(uuid),
            Vault.StateStatus.UNCONSUMED, null)
        val assetStates = serviceHub.vaultService.queryBy<SharedAssetState>(criteria).states
            .map { it.state.data }
        val assetState = assetStates.first()
        println("Retrieved shared asset state with linearId $linearId: $assetState\n")
        return assetState.toString()
    }
}

fun getSharedAssetStateAndRefWithLinearId(linearId: String, serviceHub: ServiceHub) : StateAndRef<SharedAssetState> {
    var uuid = UniqueIdentifier.Companion.fromString(linearId)
    var criteria = QueryCriteria.LinearStateQueryCriteria(null, Arrays.asList(uuid),
        Vault.StateStatus.UNCONSUMED, null)
    var assetStatesWithLinearId = serviceHub.vaultService.queryBy<SharedAssetState>(criteria).states
    if (assetStatesWithLinearId.isEmpty()) {
        println("SharedAssetState with linearId $linearId not found")
        throw NotFoundException("SharedAssetState with linearId $linearId not found")
    }

    return assetStatesWithLinearId.first()
}

/**
 * The IssueSharedAssetStateFromStateRef flow creates a fungible asset shared/co-owned by a set/group of parties from an existing state.
 * @property linearId the unique id for the shared fungible asset state [SharedAssetState] from which the state is to be created.
 */
@InitiatingFlow
@StartableByRPC
class IssueSharedAssetStateFromStateRef(val linearId: String, val coOwners: List<Party>) : FlowLogic<SignedTransaction>() {
    /**
     * The progress tracker checkpoints each stage of the flow and outputs the specified messages when each
     * checkpoint is reached in the code. See the 'progressTracker.currentStep' expressions within the call() function.
     */
    companion object {
        object GENERATING_TRANSACTION : Step("Generating transaction based on new shared fungible asset state.")
        object VERIFYING_TRANSACTION : Step("Verifying contract constraints.")
        object SIGNING_TRANSACTION : Step("Signing transaction with our private key.")
        object GATHERING_SIGS : Step("Gathering the signatures of the co-owners of the shared asset.") {
            override fun childProgressTracker() = CollectSignaturesFlow.tracker()
        }
        object FINALISING_TRANSACTION : Step("Obtaining notary signature and recording transaction.") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }

        fun tracker() = ProgressTracker(
                GENERATING_TRANSACTION,
                VERIFYING_TRANSACTION,
                SIGNING_TRANSACTION,
                GATHERING_SIGS,
                FINALISING_TRANSACTION
        )
    }

    override val progressTracker = tracker()

    /**
     * The flow logic is encapsulated within the call() method.
     */
    @Suspendable
    override fun call(): SignedTransaction {

        // Obtain a reference from a notary we wish to use.
        val notary = serviceHub.networkMapCache.notaryIdentities.single()

        // Stage 1.
        progressTracker.currentStep = GENERATING_TRANSACTION
        // Generate an unsigned transaction.

        val pointedToState: StateAndRef<SharedAssetState> = getSharedAssetStateAndRefWithLinearId(linearId, serviceHub)
        println("Retrieved shared asset state with linearId $linearId: $pointedToState\n")

        val stateStaticPointer: StaticPointer<SharedAssetState> = StaticPointer(pointedToState.ref, pointedToState.state.data.javaClass)
        val assetState: SharedAssetState = subFlow(UpdateSharedAssetCoOwnersFromPointer(stateStaticPointer, coOwners))

        val commandData: SharedAssetContract.Commands.Issue = SharedAssetContract.Commands.Issue()
        val txCommand: Command<SharedAssetContract.Commands.Issue> = Command(commandData, assetState.participants.map { it.owningKey })
        val txBuilder: TransactionBuilder = TransactionBuilder(notary)
                .addOutputState(assetState, SharedAssetContract.ID)
                .addCommand(txCommand)

        // Stage 2.
        progressTracker.currentStep = VERIFYING_TRANSACTION
        // Verify that the transaction is valid.
        txBuilder.verify(serviceHub)

        // Stage 3.
        progressTracker.currentStep = SIGNING_TRANSACTION
        // Sign the transaction.
        val partSignedTx: SignedTransaction = serviceHub.signInitialTransaction(txBuilder)
        println("Transaction submitter signed transaction.")

        // Stage 4.
        progressTracker.currentStep = GATHERING_SIGS
        // Gather signatures from the co-owners on the transaction.
        var sessions: List<FlowSession> = listOf<FlowSession>()
        var isTxSubmittedByCoOwner: Boolean = false
        for (member in coOwners) {
            if (!member.equals(ourIdentity)) {
                val membersession = initiateFlow(member)
                sessions += membersession
            } else {
                // transaction should be submitted by one of the co-owners only
                isTxSubmittedByCoOwner = true
            }
        }
        if (!isTxSubmittedByCoOwner) {
            println("Transaction submitter ($ourIdentity) has to be one of the co-owners")
            throw NotFoundException("Transaction submitter ($ourIdentity) has to be one of the co-owners")
        }
        val fullySignedTx: SignedTransaction = subFlow(CollectSignaturesFlow(partSignedTx, sessions, GATHERING_SIGS.childProgressTracker()))

        // Stage 5.
        progressTracker.currentStep = FINALISING_TRANSACTION
        // Notarise and record the transaction in all parties' vaults.
        return subFlow(FinalityFlow(fullySignedTx, sessions, FINALISING_TRANSACTION.childProgressTracker()))
    }
}

@InitiatedBy(IssueSharedAssetStateFromStateRef::class)
class IssueSharedAssetFromStateRefAcceptor(val session: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val signTransactionFlow = object : SignTransactionFlow(session) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat {
            }
        }
        try {
            val txId = subFlow(signTransactionFlow).id
            println("Co-owner ($ourIdentity) signed issue shared asset from state-ref transaction.")
            return subFlow(ReceiveFinalityFlow(session, expectedTxId = txId))
        } catch (e: Exception) {
            println("Error signing issue shared asset from state-ref transaction by co-owner ($ourIdentity): ${e.message}\n")
            return subFlow(ReceiveFinalityFlow(session))
        }
    }
}

@InitiatingFlow
@StartableByRPC
class MergeSharedAssetStates(val linearId1: String, val linearId2: String) : FlowLogic<SignedTransaction>() {
    /**
     * The progress tracker checkpoints each stage of the flow and outputs the specified messages when each
     * checkpoint is reached in the code. See the 'progressTracker.currentStep' expressions within the call() function.
     */
    companion object {
        object GENERATING_TRANSACTION : Step("Generating transaction based on input shared fungible asset states.")
        object VERIFYING_TRANSACTION : Step("Verifying contract constraints.")
        object SIGNING_TRANSACTION : Step("Signing transaction with our private key.")
        object GATHERING_SIGS : Step("Gathering the signatures of the co-owners of the shared asset.") {
            override fun childProgressTracker() = CollectSignaturesFlow.tracker()
        }
        object FINALISING_TRANSACTION : Step("Obtaining notary signature and recording transaction.") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }

        fun tracker() = ProgressTracker(
            GENERATING_TRANSACTION,
            VERIFYING_TRANSACTION,
            SIGNING_TRANSACTION,
            GATHERING_SIGS,
            FINALISING_TRANSACTION
        )
    }

    override val progressTracker = tracker()

    /**
     * The flow logic is encapsulated within the call() method.
     */
    @Suspendable
    override fun call(): SignedTransaction {

        // Obtain a reference from a notary we wish to use.
        val notary = serviceHub.networkMapCache.notaryIdentities.single()

        // Stage 1.
        progressTracker.currentStep = GENERATING_TRANSACTION
        // Generate an unsigned transaction.

        val assetState1: StateAndRef<SharedAssetState> = getSharedAssetStateAndRefWithLinearId(linearId1, serviceHub)
        val assetState2: StateAndRef<SharedAssetState> = getSharedAssetStateAndRefWithLinearId(linearId2, serviceHub)
        if (assetState1.state.data.coOwners.toSet() != assetState2.state.data.coOwners.toSet()) {
            println("Merging asset states is not possible as they don't belong to the same set of co-owners")
            throw NotFoundException("Merging asset states is not possible as they don't belong to the same set of co-owners")
        }

        println("Merging asset states from the ledger: ${assetState1.state.data} and ${assetState2.state.data}\n")

        val mergedState: SharedAssetState = SharedAssetState(assetState1.state.data.quantity + assetState2.state.data.quantity, assetState1.state.data.type, assetState1.state.data.coOwners)

        println("Merged asset state proposed: ${mergedState}\n")

        val txCommand = Command(SharedAssetContract.Commands.Merge(), mergedState.participants.map { it.owningKey })
        val txBuilder = TransactionBuilder(notary)
            .addInputState(assetState1)
            .addInputState(assetState2)
            .addOutputState(mergedState, SharedAssetContract.ID)
            .addCommand(txCommand)

        // Stage 2.
        progressTracker.currentStep = VERIFYING_TRANSACTION
        // Verify that the transaction is valid.
        txBuilder.verify(serviceHub)
        println("Transaction verified")

        // Stage 3.
        progressTracker.currentStep = SIGNING_TRANSACTION
        // Sign the transaction.
        val partSignedTx: SignedTransaction = serviceHub.signInitialTransaction(txBuilder)
        println("Transaction submitter signed transaction.")

        // Stage 4.
        progressTracker.currentStep = GATHERING_SIGS
        // Gather signatures from the co-owners on the transaction.
        var sessions = listOf<FlowSession>()
        var isTxSubmittedByCoOwner: Boolean = false
        for (member in mergedState.coOwners) {
            if (!member.equals(ourIdentity)) {
                val membersession = initiateFlow(member)
                sessions += membersession
            } else {
                // transaction should be submitted by one of the co-owners only
                isTxSubmittedByCoOwner = true
            }
        }
        if (!isTxSubmittedByCoOwner) {
            println("Transaction submitter ($ourIdentity) has to be one of the co-owners")
            throw NotFoundException("Transaction submitter ($ourIdentity) has to be one of the co-owners")
        }
        val fullySignedTx: SignedTransaction = subFlow(CollectSignaturesFlow(partSignedTx, sessions, GATHERING_SIGS.childProgressTracker()))

        // Stage 5.
        progressTracker.currentStep = FINALISING_TRANSACTION
        // Notarise and record the transaction in all parties' vaults.
        return subFlow(FinalityFlow(fullySignedTx, sessions, FINALISING_TRANSACTION.childProgressTracker()))
    }
}

@InitiatedBy(MergeSharedAssetStates::class)
class MergeSharedAssetStatesAcceptor(val session: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val signTransactionFlow = object : SignTransactionFlow(session) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat {
            }
        }
        try {
            val txId = subFlow(signTransactionFlow).id
            println("Co-owner ($ourIdentity) signed merge shared assets transaction.")
            return subFlow(ReceiveFinalityFlow(session, expectedTxId = txId))
        } catch (e: Exception) {
            println("Error signing merge shared assets transaction by co-owner ($ourIdentity): ${e.message}\n")
            return subFlow(ReceiveFinalityFlow(session))
        }
    }
}

/**
 * The RetrieveSharedAssetStateAndRef flow is used to retrieve an [SharedAssetState] from the vault based on the type and quantity.
 *
 * @property assetType the filter for the [SharedAssetState] list to be retrieved.
 * @property quantity the number of units of fungible asset to be part of the [SharedAssetState] to be retrieved.
 */
@InitiatingFlow
@StartableByRPC
class RetrieveSharedAssetStateAndRef(private val assetType: String, private val quantity: Long) : FlowLogic<StateAndRef<SharedAssetState>?>() {

    override fun call(): StateAndRef<SharedAssetState>? {

        val states = serviceHub.vaultService.queryBy<SharedAssetState>().states
            .filter { it.state.data.type == assetType }
            .filter { it.state.data.quantity == quantity }

        var fetchedState: StateAndRef<SharedAssetState>? = null

        if (states.isNotEmpty()) {
            fetchedState = states.first()
            println("Retrieved state with type $assetType: $fetchedState\n")
        } else {
            println("No state found with type $assetType\n")
        }

        return fetchedState
    }
}

@InitiatingFlow
@StartableByRPC
class SplitSharedAssetState(val linearId: String, val quantity1: Long, val quantity2: Long) : FlowLogic<SignedTransaction>() {
    /**
     * The progress tracker checkpoints each stage of the flow and outputs the specified messages when each
     * checkpoint is reached in the code. See the 'progressTracker.currentStep' expressions within the call() function.
     */
    companion object {
        object GENERATING_TRANSACTION : Step("Generating transaction based on the fungible token asset state.")
        object VERIFYING_TRANSACTION : Step("Verifying contract constraints.")
        object SIGNING_TRANSACTION : Step("Signing transaction with our private key.")
        object GATHERING_SIGS : Step("Gathering the signatures of the co-owners of the shared asset.") {
            override fun childProgressTracker() = CollectSignaturesFlow.tracker()
        }
        object FINALISING_TRANSACTION : Step("Obtaining notary signature and recording transaction.") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }

        fun tracker() = ProgressTracker(
            GENERATING_TRANSACTION,
            VERIFYING_TRANSACTION,
            SIGNING_TRANSACTION,
            GATHERING_SIGS,
            FINALISING_TRANSACTION
        )
    }

    override val progressTracker = tracker()

    /**
     * The flow logic is encapsulated within the call() method.
     */
    @Suspendable
    override fun call(): SignedTransaction {

        // Obtain a reference from a notary we wish to use.
        val notary = serviceHub.networkMapCache.notaryIdentities.single()

        // Stage 1.
        progressTracker.currentStep = GENERATING_TRANSACTION
        // Generate an unsigned transaction.

        val splitState: StateAndRef<SharedAssetState> = getSharedAssetStateAndRefWithLinearId(linearId, serviceHub)
        println("Split asset state from the ledger: $splitState.state.data\n")

        val outputState1 = SharedAssetState(quantity1, splitState.state.data.type, splitState.state.data.coOwners)
        val outputState2 = SharedAssetState(quantity2, splitState.state.data.type, splitState.state.data.coOwners)
        println("Proposed states after split: ${outputState1} and ${outputState2}\n")

        val txCommand = Command(SharedAssetContract.Commands.Split(), splitState.state.data.participants.map { it.owningKey })
        val txBuilder = TransactionBuilder(notary)
            .addInputState(splitState)
            .addOutputState(outputState1, SharedAssetContract.ID)
            .addOutputState(outputState2, SharedAssetContract.ID)
            .addCommand(txCommand)

        // Stage 2.
        progressTracker.currentStep = VERIFYING_TRANSACTION
        // Verify that the transaction is valid.
        txBuilder.verify(serviceHub)
        println("Transaction verified")

        // Stage 3.
        progressTracker.currentStep = SIGNING_TRANSACTION
        // Sign the transaction.
        val partSignedTx: SignedTransaction = serviceHub.signInitialTransaction(txBuilder)
        println("Transaction submitter signed transaction.")

        // Stage 4.
        progressTracker.currentStep = GATHERING_SIGS
        // Gather signatures from the co-owners on the transaction.
        var sessions = listOf<FlowSession>()
        var isTxSubmittedByCoOwner: Boolean = false
        for (member in splitState.state.data.coOwners) {
            if (!member.equals(ourIdentity)) {
                val membersession = initiateFlow(member)
                sessions += membersession
            } else {
                // transaction should be submitted by one of the co-owners only
                isTxSubmittedByCoOwner = true
            }
        }
        if (!isTxSubmittedByCoOwner) {
            println("Transaction submitter ($ourIdentity) has to be one of the co-owners")
            throw NotFoundException("Transaction submitter ($ourIdentity) has to be one of the co-owners")
        }
        val fullySignedTx: SignedTransaction = subFlow(CollectSignaturesFlow(partSignedTx, sessions, GATHERING_SIGS.childProgressTracker()))

        // Stage 5.
        progressTracker.currentStep = FINALISING_TRANSACTION
        // Notarise and record the transaction in all parties' vaults.
        return subFlow(FinalityFlow(fullySignedTx, sessions, FINALISING_TRANSACTION.childProgressTracker()))
    }
}

@InitiatedBy(SplitSharedAssetState::class)
class SplitSharedAssetStateAcceptor(val session: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val signTransactionFlow = object : SignTransactionFlow(session) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat {
            }
        }
        try {
            val txId = subFlow(signTransactionFlow).id
            println("Co-owner ($ourIdentity) signed split shared asset transaction.")
            return subFlow(ReceiveFinalityFlow(session, expectedTxId = txId))
        } catch (e: Exception) {
            println("Error signing split shared asset transaction by co-owner ($ourIdentity): ${e.message}\n")
            return subFlow(ReceiveFinalityFlow(session))
        }
    }
}

@InitiatingFlow
@StartableByRPC
class TransferSharedAssetStateInitiator(val linearId: String, val updatedCoOwners: List<Party>) : FlowLogic<SignedTransaction>() {
    /**
     * The progress tracker checkpoints each stage of the flow and outputs the specified messages when each
     * checkpoint is reached in the code. See the 'progressTracker.currentStep' expressions within the call() function.
     */
    companion object {
        object GENERATING_TRANSACTION : Step("Generating transaction based on new fungible token asset state.")
        object VERIFYING_TRANSACTION : Step("Verifying contract constraints.")
        object SIGNING_TRANSACTION : Step("Signing transaction with our private key.")
        object GATHERING_SIGS : Step("Gathering the counterparty's signature.") {
            override fun childProgressTracker() = CollectSignaturesFlow.tracker()
        }
        object FINALISING_TRANSACTION : Step("Obtaining notary signature and recording transaction.") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }

        fun tracker() = ProgressTracker(
            GENERATING_TRANSACTION,
            VERIFYING_TRANSACTION,
            SIGNING_TRANSACTION,
            GATHERING_SIGS,
            FINALISING_TRANSACTION
        )
    }

    override val progressTracker = tracker()

    /**
     * The flow logic is encapsulated within the call() method.
     */
    @Suspendable
    override fun call(): SignedTransaction {

        // Obtain a reference from a notary we wish to use.
        val notary = serviceHub.networkMapCache.notaryIdentities.single()

        // Stage 1.
        progressTracker.currentStep = GENERATING_TRANSACTION
        // Generate an unsigned transaction.

        val inputState: StateAndRef<SharedAssetState> = getSharedAssetStateAndRefWithLinearId(linearId, serviceHub)
        println("The shared asset that will be transferred is: $inputState.state.data\n")

        val outputState = inputState.state.data.copy(coOwners = updatedCoOwners)
        println("Updated shared asset state in the ledger is: $outputState\n")

        var requiredSigners: List<PublicKey> = listOf<PublicKey>()
        for (owner in inputState.state.data.coOwners) {
            requiredSigners += owner.owningKey
        }
        for (owner in outputState.coOwners) {
            requiredSigners += owner.owningKey
        }

        val txCommand = Command(SharedAssetContract.Commands.Transfer(), requiredSigners)
        val txBuilder = TransactionBuilder(notary)
            .addInputState(inputState)
            .addOutputState(outputState, SharedAssetContract.ID)
            .addCommand(txCommand)

        // Stage 2.
        progressTracker.currentStep = VERIFYING_TRANSACTION
        // Verify that the transaction is valid.
        txBuilder.verify(serviceHub)
        println("Transaction verified")

        // Stage 3.
        progressTracker.currentStep = SIGNING_TRANSACTION
        // Sign the transaction.
        val partSignedTx: SignedTransaction = serviceHub.signInitialTransaction(txBuilder)
        println("Transaction submitter signed transaction.")

        // Stage 4.
        progressTracker.currentStep = GATHERING_SIGS
        // Gather signatures from the co-owners on the transaction.
        
        var sessions = listOf<FlowSession>()

        for (member in outputState.coOwners) {
            if (!member.equals(ourIdentity)) {
                val membersession = initiateFlow(member)
                sessions += membersession
            }
        }

        var isTxSubmittedByCoOwner: Boolean = false
        for (member in inputState.state.data.coOwners) {
            if (!member.equals(ourIdentity)) {
                val membersession = initiateFlow(member)
                sessions += membersession
            } else {
                // transaction should be submitted by one of the co-owners only
                isTxSubmittedByCoOwner = true
            }
        }
        if (!isTxSubmittedByCoOwner) {
            println("Transaction submitter ($ourIdentity) has to be one of the co-owners of the state with input linearId: $linearId")
            throw NotFoundException("Transaction submitter ($ourIdentity) has to be one of the co-owners of the state with input linearId: $linearId")
        }
        val fullySignedTx: SignedTransaction = subFlow(CollectSignaturesFlow(partSignedTx, sessions, GATHERING_SIGS.childProgressTracker()))

        // Stage 5.
        progressTracker.currentStep = FINALISING_TRANSACTION
        // Notarise and record the transaction in all parties' vaults.
        return subFlow(FinalityFlow(fullySignedTx, sessions, FINALISING_TRANSACTION.childProgressTracker()))
    }
}

/**
 * This flow enables the [TransferSharedAssetAcceptor] to respond to the asset transfer initiated by [TransferSharedAssetStateInitiator]
 */
@InitiatedBy(TransferSharedAssetStateInitiator::class)
class TransferSharedAssetAcceptor(val session: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val signTransactionFlow = object : SignTransactionFlow(session) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat {
            }
        }
        try {
            val txId = subFlow(signTransactionFlow).id
            println("Co-owner ($ourIdentity) signed shared asset transfer transaction.")
            return subFlow(ReceiveFinalityFlow(session, expectedTxId = txId))
        } catch (e: Exception) {
            println("Error signing shared asset transfer transaction by co-owner ($ourIdentity): ${e.message}\n")
            return subFlow(ReceiveFinalityFlow(session))
        }
    }
}

/**
 * The getSharedAssetJsonStringFromStatePointer function fetches the shared fungible asset state from its state pointer [AssetPledgeState].assetStatePointer
 * and creates marshalled JSON encoded object which is returned.
 * This function is called by the exporting network in the context of interop-query from the importing network
 * to the exporting network before performing claim on remote shared fungible asset.
 *
 * @property assetPledgeState The (interop) vault state that represents the pledge details on the ledger.
 */
fun getSharedAssetJsonStringFromStatePointer(assetPledgeState: AssetPledgeState, serviceHub: ServiceHub) : String {

    if (assetPledgeState.assetStatePointer == null) {
        // Typically, [AssetPledgeState].assetStatePointer will be null only in the case of pledge details not
        // being available for a given pledgeId. The flow GetAssetPledgeStatus in AssetTransferFlows sets this
        // pointer to null if the pledge-state is not available in the context of the interop-query from the
        // importing n/w to the exporting n/w. Hence return empty string, and this will not be passed to the
        // JSON unmarshalling method GetSimpleSharedAssetStateAndContractId since the expiryTime will be elapsed for the
        // claim to happen (i.e., if assetStatePointer is null, then expiryTimeSecs will be set to past time).
        return ""
    }

    val assetStatePointer: StaticPointer<ContractState> = assetPledgeState.assetStatePointer!!
    val assetState = assetStatePointer.resolve(serviceHub).state.data as SharedAssetState

    println("Creating simple shared fungible asset JSON from StatePointer.")
    var coOwners: List<String> = listOf<String>()
    coOwners += assetPledgeState.lockerCert
    var marshalledAssetJson =
        marshalSharedFungibleAsset(assetState.type, assetState.quantity, coOwners)

    return marshalledAssetJson
}

/**
 * The GetSharedAssetPledgeStatusByPledgeId flow fetches the shared fungible asset pledge status in the exporting network and returns as byte array.
 * It is called during the interop query by importing network before performing the claim on fungible asset pledged in exporting network.
 *
 * @property pledgeId The unique identifier representing the pledge on an asset for transfer, in the exporting n/w.
 * @property recipientNetworkId The id of the network in which the pledged asset will be claimed.
 */
@InitiatingFlow
@StartableByRPC
class GetSharedAssetPledgeStatusByPledgeId(
    val pledgeId: String,
    val recipientNetworkId: String
) : FlowLogic<ByteArray>() {
    @Suspendable
    override fun call(): ByteArray {

        var assetPledgeState: AssetPledgeState = subFlow(GetAssetPledgeStatus(pledgeId, recipientNetworkId))
        println("Obtained [AssetPledgeState] vault state: ${assetPledgeState}.\n")
        val marshalledAssetJson = getSharedAssetJsonStringFromStatePointer(assetPledgeState, serviceHub)

        return subFlow(AssetPledgeStateToProtoBytes(assetPledgeState, marshalledAssetJson))
    }
}

/**
 * The GetSharedAssetClaimStatusByPledgeId flow fetches the shared fungible asset claim status in the importing network and returns as byte array.
 * It is called during the interop query by exporting network before performing the re-claim on asset pledged in exporting network.
 *
 * @property pledgeId The unique identifier representing the pledge on an asset for transfer, in the exporting n/w.
 * @property expiryTimeSecs The time epoch seconds after which re-claim of the asset is allowed.
 */
@InitiatingFlow
@StartableByRPC
class GetSharedAssetClaimStatusByPledgeId(
    val pledgeId: String,
    val expiryTimeSecs: String
) : FlowLogic<ByteArray>() {
    /**
     * The call() method captures the logic to fetch [AssetClaimStatusState] vault state from importing n/w.
     *
     * @return Returns ByteArray.
     */
    @Suspendable
    override fun call(): ByteArray {

        println("Inside GetSharedAssetClaimStatusByPledgeId(), pledgeId: $pledgeId and expiryTimeSecs: $expiryTimeSecs.")

        println("Creating empty simple shared fungible asset JSON.")
        var coOwners: List<String> = listOf<String>()
        coOwners += "dummy-owner-cert"
        var marshalledBlankAssetJson = marshalSharedFungibleAsset("dummy-simple-asset", 0L, coOwners)

        return subFlow(GetAssetClaimStatusState(pledgeId, expiryTimeSecs, marshalledBlankAssetJson))
    }
}

/**
 * The GetSimpleSharedAssetStateAndContractId flow first checks if the JSON encoded object corresponds to the specified simple shared asset attribute values.
 * It first unmarshalls the passed JSON encoded object and verifies if the attribte values match with the input values. Then, it creates
 * the asset state object corresponding to the JSON object passed as input.
 *
 * Note: This function is passed as an argument to resolveGetAssetStateAndContractIdFlow() during reclaim-pledged asset transaction.
 * This function has five arguements. A similar function is implemented for FungibleHouseToken and SimpleBondAsset with
 * the "same number and type" of arguements. Based on the function name passed, <ContractId, State> will be fetched at runtime.
 *
 * @property marshalledAsset The JSON encoded fungible simple asset.
 * @property type The fungible simple asset type.
 * @property quantity The number of units of the fungible simple asset, passed as String.
 * @property lockerCert The owner (certificate in base64 of the exporting network) of the fungible asset before asset-transfer.
 * @property holders The parties that co-own the shared fungible simple asset after asset-transfer.
 */
@InitiatingFlow
@StartableByRPC
class GetSimpleSharedAssetStateAndContractId(
    val marshalledAsset: String,
    val type: String,
    val quantity: Long,
    val lockerCert: String,
    val holders: List<Party>
): FlowLogic<Pair<String, SharedAssetState>>() {
    @Suspendable
    override fun call(): Pair<String, SharedAssetState> {

        println("Inside GetSimpleAssetStateAndContractId().")

        // must have used GsonBuilder().create().toJson() at the time of serialization of the JSON
        val pledgedSharedFungibleAsset = Gson().fromJson(marshalledAsset, SharedAssetStateJSON::class.java)
        println("Unmarshalled shared fungible simple asset is: $pledgedSharedFungibleAsset")

        if (pledgedSharedFungibleAsset.type != type) {
            println("pledgedSharedFungibleAsset.type(${pledgedSharedFungibleAsset.type}) need to match with type(${type}).")
            throw Exception("pledgedSharedFungibleAsset.type(${pledgedSharedFungibleAsset.type}) need to match with type(${type}).")
        } else if (pledgedSharedFungibleAsset.quantity != quantity) {
            println("pledgedSharedFungibleAsset.numunits(${pledgedSharedFungibleAsset.quantity}) need to match with quantity(${quantity}).")
            throw Exception("pledgedSharedFungibleAsset.numUnits(${pledgedSharedFungibleAsset.quantity}) need to match with quantity(${quantity}).")
        } else if (!pledgedSharedFungibleAsset.certsOfCoOwners.contains(lockerCert)) {
            println("pledgedSharedFungibleAsset.coowners(${pledgedSharedFungibleAsset.certsOfCoOwners}) need to contain the lockerCert(${lockerCert}).")
            throw Exception("pledgedSharedFungibleAsset.coowners(${pledgedSharedFungibleAsset.certsOfCoOwners}) need to contain the lockerCert(${lockerCert}).")
        }

        val simpleasset = SharedAssetState(
            pledgedSharedFungibleAsset.quantity, // @property quantity
            pledgedSharedFungibleAsset.type, // @property type
            holders // @property coOwners
        )

        return Pair(SharedAssetContract.ID, simpleasset)
    }
}

/**
 * The marshalSharedFungibleAsset function is used to obtain the JSON encoding of the shared fungible asset of interest to the user.
 * This function is typically called by the application client which may not know the full details of the asset.
 *
 * @property type The shared fungible asset type.
 * @property quantity The number of units of the shared fungible asset.
 * @property certsOfCoOwners The certificates of the co-owners of shared asset in base64 form
 */
fun marshalSharedFungibleAsset(type: String, quantity: Long, certsOfCoOwners: List<String>) : String {

    val assetJson = SharedAssetStateJSON(
        type = type,
        quantity = quantity,
        certsOfCoOwners = certsOfCoOwners
    )

    println("Inside marshalSharedFungibleAsset(), created shared fungible asset: $assetJson\n.")
    val gson = GsonBuilder().create();
    // must use Gson().fromJson() at the time of deserialization of the JSON
    var marshalledAssetJson = gson.toJson(assetJson, SharedAssetStateJSON::class.java)

    return marshalledAssetJson
}