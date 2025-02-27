/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.weaver.corda.app.interop.flows

import arrow.core.*
import co.paralleluniverse.fibers.Suspendable
import com.weaver.corda.app.interop.contracts.AssetExchangeHTLCStateContract
import com.weaver.corda.app.interop.states.AssetExchangeHTLCState
import com.weaver.corda.app.interop.states.AssetLockHTLCData
import com.weaver.corda.app.interop.states.AssetClaimHTLCData
import com.weaver.corda.app.interop.contracts.AssetExchangeTxStateContract
import com.weaver.corda.app.interop.states.AssetExchangeTxState

import net.corda.core.contracts.ContractState
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Command
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.TimeWindow
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.requireThat
import net.corda.core.contracts.StaticPointer

import net.corda.core.identity.Party

import net.corda.core.flows.*
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.ServiceHub
import net.corda.core.node.StatesToRecord
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.unwrap
import net.corda.core.serialization.CordaSerializable
import java.time.Instant
import java.util.Base64
import java.security.PublicKey

/**
 * Enum for communicating the role of the responder from initiator flow to
 * responder flow.
 */

@CordaSerializable
enum class ResponderRole {
  LOCKER, RECIPIENT, ISSUER, OBSERVER
}
    
/**
 * The LockAssetHTLCFlows flow is used to create a lock for an asset using HTLC.
 *
 * @property assetExchangeHTLCState The [AssetExchangeHTLCState] provided by the Corda client to create a lock.
 */

object LockAssetHTLC {
    @InitiatingFlow
    @StartableByRPC
    class Initiator
    @JvmOverloads
    constructor(
            val lockInfo: AssetLockHTLCData,
            val assetStateRef: StateAndRef<ContractState>,
            val assetStateDeleteCommand: CommandData,
            val recipients: List<Party>,
            val issuer: Party,
            val observers: List<Party> = listOf<Party>(),
            val coOwners: List<Party> = listOf<Party>()
    ) : FlowLogic<Either<Error, UniqueIdentifier>>() {
        /**
         * The call() method captures the logic to create a new [AssetExchangeHTLCState] state in the vault.
         *
         * It first creates a new AssetExchangeHTLCState. It then builds
         * and verifies the transaction, collects the required signatures,
         * and stores the state in the vault.
         *
         * @return Returns the linearId of the newly created [AssetExchangeHTLCState].
         */
        @Suspendable
        override fun call(): Either<Error, UniqueIdentifier> = try {
            val assetExchangeHTLCState = AssetExchangeHTLCState(
                lockInfo,
                StaticPointer(assetStateRef.ref, assetStateRef.state.data.javaClass), //Get the state pointer from StateAndRef
                coOwners,
                recipients
            )
        
            println("Creating Lock State ${assetExchangeHTLCState}")
            // 2. Build the transaction
            val notary = assetStateRef.state.notary
            var participantKeys: List<PublicKey> = listOf<PublicKey>()
            for (member in assetExchangeHTLCState.lockers) {
                participantKeys += member.owningKey
            }
            for (member in assetExchangeHTLCState.recipients) {
                participantKeys += member.owningKey
            }
            val lockCmd = Command(AssetExchangeHTLCStateContract.Commands.Lock(), participantKeys)

            var requiredSigners: List<PublicKey> = listOf<PublicKey>()
            for (member in assetExchangeHTLCState.lockers) {
                requiredSigners += member.owningKey
            }
            requiredSigners += issuer.owningKey

            var isTxSubmittedByCoOwner: Boolean = false
            coOwners.forEach {
                val member = it
                if (member.equals(ourIdentity)) {
                    // transaction should be submitted by one of the co-owners only
                    isTxSubmittedByCoOwner = true
                }
            }
            if ((coOwners.size > 0) && (!isTxSubmittedByCoOwner)) {
                println("Transaction submitter ($ourIdentity) has to be one of the co-owners")
                throw Exception("Transaction submitter ($ourIdentity) has to be one of the co-owners")
            }

            val assetDeleteCmd = Command(assetStateDeleteCommand, requiredSigners)

            val txBuilder = TransactionBuilder(notary)
                    .addInputState(assetStateRef)
                    .addOutputState(assetExchangeHTLCState, AssetExchangeHTLCStateContract.ID)
                    .addCommand(lockCmd)
                    .addCommand(assetDeleteCmd)

            // 3. Verify and collect signatures on the transaction
            txBuilder.verify(serviceHub)
            val partSignedTx = serviceHub.signInitialTransaction(txBuilder)
            println("Locker signed transaction.")
            
            var sessions = listOf<FlowSession>()
            // Add a session for each co-owner other than the submitter, for the shared assets
            coOwners.forEach {
                if (!ourIdentity.equals(it)) {
                    val coOwnerSession = initiateFlow(it)
                    coOwnerSession.send(ResponderRole.LOCKER)
                    sessions += coOwnerSession
                }
            }

            // Add a session for each recipient co-owner, for the shared assets
            recipients.forEach {
                if (!ourIdentity.equals(it)) {
                    val recipientCoOwnerSession = initiateFlow(it)
                    recipientCoOwnerSession.send(ResponderRole.RECIPIENT)
                    sessions += recipientCoOwnerSession
                }
            }

            /// Add issuer session if recipient or locker (i.e. me) is not issuer
            if (!recipients.contains(issuer) && !ourIdentity.equals(issuer)) {
                val issuerSession = initiateFlow(issuer)
                issuerSession.send(ResponderRole.ISSUER)
                sessions += issuerSession
            }
            val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, sessions))

            var observerSessions = listOf<FlowSession>()
            for (obs in observers) {
                val obsSession = initiateFlow(obs)
                obsSession.send(ResponderRole.OBSERVER)
                observerSessions += obsSession
            }
            val storedAssetExchangeHTLCState = subFlow(FinalityFlow(
                fullySignedTx, 
                sessions + observerSessions)).tx.outputStates.first() as AssetExchangeHTLCState
            
            // 4. Return the linearId of the state
            println("Successfully stored: $storedAssetExchangeHTLCState\n")
            Right(storedAssetExchangeHTLCState.linearId)
        } catch (e: Exception) {
            println("Error locking asset: ${e.message}\n")
            Left(Error("Failed to lock asset: ${e.message}"))
        }
    }
    
    @InitiatedBy(Initiator::class)
    class Acceptor(val session: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val role = session.receive<ResponderRole>().unwrap { it }
            if (role == ResponderRole.ISSUER) {
                val signTransactionFlow = object : SignTransactionFlow(session) {
                    override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    }
                }
                try {
                    val txId = subFlow(signTransactionFlow).id
                    println("Issuer signed transaction.")
                    return subFlow(ReceiveFinalityFlow(session, expectedTxId = txId))
                } catch (e: Exception) {
                    println("Error signing unlock asset transaction by Issuer: ${e.message}\n")
                    return subFlow(ReceiveFinalityFlow(session))
                }
            } else if (role == ResponderRole.LOCKER) {
                val signTransactionFlow = object : SignTransactionFlow(session) {
                    override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    }
                }
                try {
                    val txId = subFlow(signTransactionFlow).id
                    println("Co-owner ($ourIdentity) signed shared asset lock transaction.")
                    return subFlow(ReceiveFinalityFlow(session, expectedTxId = txId))
                } catch (e: Exception) {
                    println("Error signing shared asset lock transaction by co-owner ($ourIdentity): ${e.message}\n")
                    return subFlow(ReceiveFinalityFlow(session))
                }
            } else if (role == ResponderRole.RECIPIENT) {
              val signTransactionFlow = object : SignTransactionFlow(session) {
                  override fun checkTransaction(stx: SignedTransaction) = requireThat {
                      "The output State must be AssetExchangeHTLCState" using (stx.tx.outputs.single().data is AssetExchangeHTLCState)
                      val htlcState = stx.tx.outputs.single().data as AssetExchangeHTLCState
                      "I must be the recipient" using (htlcState.recipients.contains(ourIdentity))
                  }
              }
              try {
                  val txId = subFlow(signTransactionFlow).id
                  println("Recipient signed transaction.")
                  return subFlow(ReceiveFinalityFlow(session, expectedTxId = txId))
              } catch (e: Exception) {
                  println("Error signing unlock asset transaction by Recipient: ${e.message}\n")
                  return subFlow(ReceiveFinalityFlow(session))
              }
            } else if (role == ResponderRole.OBSERVER) {
                val sTx = subFlow(ReceiveFinalityFlow(session, statesToRecord = StatesToRecord.ALL_VISIBLE))
                println("Received Tx: ${sTx}")
                return sTx
            } else {
                println("Incorrect Responder Role.")
                throw IllegalStateException("Incorrect Responder Role.")
            }
        }
    }

}

/**
 * The IsAssetLockedHTLC flow is used to check if an asset is locked.
 *
 * @property linearId The unique identifier for an AssetExchangeHTLCState.
 */
@StartableByRPC
class IsAssetLockedHTLC(
        val contractId: String
) : FlowLogic<Boolean>() {
    /**
     * The call() method captures the logic to check if asset is locked.
     *
     * @return Returns Boolean True/False
     */
    @Suspendable
    override fun call(): Boolean {
        val linearId = getLinearIdFromString(contractId)
        println("Getting AssetExchangeHTLCState for linearId $linearId.")
        val states = serviceHub.vaultService.queryBy<AssetExchangeHTLCState>(
            QueryCriteria.LinearStateQueryCriteria(linearId = listOf(linearId))
        ).states
        if (states.isEmpty()) {
            println("No states found")
            return false
        } else {
            val htlcState = states.first().state.data
            println("Got AssetExchangeHTLCState: ${htlcState}")
            if (Instant.now().isAfter(htlcState.lockInfo.expiryTime)) {
                return false
            }
            return true
        }
    }
}

/**
 * The GetAssetExchangeHTLCStateById flow is used to fetch an existing AssetExchangeHTLCState.
 *
 * @property linearId The unique identifier for an AssetExchangeHTLCState.
 */
@StartableByRPC
class GetAssetExchangeHTLCStateById(
        val contractId: String
) : FlowLogic<Either<Error, StateAndRef<AssetExchangeHTLCState>>>() {
    /**
     * The call() method captures the logic to fetch the AssetExchangeHTLCState.
     *
     * @return Returns Either<Error, StateAndRef<AssetExchangeHTLCState>>
     */
    @Suspendable
    override fun call(): Either<Error, StateAndRef<AssetExchangeHTLCState>> = try {
        val linearId = getLinearIdFromString(contractId)
        println("Getting AssetExchangeHTLCState for contractId $linearId.")
        val states = serviceHub.vaultService.queryBy<AssetExchangeHTLCState>(
            QueryCriteria.LinearStateQueryCriteria(linearId = listOf(linearId))
        ).states
        if (states.isEmpty()) {
            Left(Error("AssetExchangeHTLCState for Id: ${linearId} not found."))
        } else {
            println("Got AssetExchangeHTLCState: ${states.first().state.data}")
            Right(states.first())
        }
    } catch (e: Exception) {
        println("Error fetching state from the vault: ${e.message}\n")
        Left(Error("Error fetching state from the vault: ${e.message}"))
    }
}

/**
 * The GetAssetExchangeHTLCHashById flow is used to fetch the base64 value (as byte array) of the hash used in asset exchange.
 *
 * @property linearId The unique identifier for an AssetExchangeHTLCState.
 */
@StartableByRPC
class GetAssetExchangeHTLCHashById(
    val contractId: String
) : FlowLogic<ByteArray>() {
    /**
     * The call() method captures the logic to fetch the base64 value of hash used for asset exchange.
     *
     * @return Returns ByteArray
     */
    @Suspendable
    override fun call(): ByteArray {
        val linearId = getLinearIdFromString(contractId)
        println("Getting AssetExchangeHTLCState for linearId $linearId.")
        val states = serviceHub.vaultService.queryBy<AssetExchangeHTLCState>(
            QueryCriteria.LinearStateQueryCriteria(linearId = listOf(linearId))
        ).states
        if (states.isEmpty()) {
            println("No states found")
            throw NullPointerException("No such AssetExchangeHTLCState with linearId $linearId exists.")
        } else {
            val htlcState = states.first().state.data
            val hashBase64 = Base64.getEncoder().encodeToString(htlcState.lockInfo.hash.bytes).toByteArray()
            println("HashBase64: ${hashBase64.toString(Charsets.UTF_8)}")
            return hashBase64
        }
    }
}

/**
 * The GetAssetExchangeHTLCHashPreImageById flow is used to fetch the hash preimage part of an asset exchange transaction.
 *
 * @property linearId The unique identifier for an AssetExchangeTxState.
 */
@StartableByRPC
class GetAssetExchangeHTLCHashPreImageById(
    val contractId: String
) : FlowLogic<ByteArray>() {
    /**
     * The call() method captures the logic to fetch the hash preimage used in an asset exchange transaction.
     *
     * @return Returns ByteArray
     */
    @Suspendable
    override fun call(): ByteArray {
        val linearId = getLinearIdFromString(contractId)
        println("Getting AssetExchangeTxState for linearId $linearId.")
        val states = serviceHub.vaultService.queryBy<AssetExchangeTxState>(
            QueryCriteria.LinearStateQueryCriteria(linearId = listOf(linearId))
        ).states
        if (states.isEmpty()) {
            println("No states found")
            throw NullPointerException("No such transaction exists which consumed AssetExchangeHTLCState with linearId $linearId.")
        } else {
            val txId = states.first().state.data.txId
            val sTx = serviceHub.validatedTransactions.getTransaction(txId)!!
            val lTx = sTx.tx.toLedgerTransaction(serviceHub)
            val claimCmd = lTx.commandsOfType<AssetExchangeHTLCStateContract.Commands.Claim>().single()
            val secret = claimCmd.value.assetClaimHTLC.hashPreimage.bytes
            println("Hash Pre-Image: ${secret.toString(Charsets.UTF_8)}")
            return secret
        }
    }
}

fun tryResolveUpdateOwnerFlowAlternatives(flowName: String, assetStatePointer: StaticPointer<ContractState>,
    updatedCoOwners: List<Party>) : Either<Error, FlowLogic<ContractState>> {

    return try {
        var result: Either<Error, FlowLogic<ContractState>> = resolveUpdateOwnerFlow(flowName, listOf(assetStatePointer, updatedCoOwners))
        when (result) {
            /*
                resolveUpdateOwnerFlow(flowName, listOf(assetStatePointer, updatedCoOwners)).fold({
                    resolveUpdateOwnerFlow(flowName, listOf(assetStatePointer)).fold({
                        println("Error: Unable to resolve flow $flowName.\n")
                        Left(Error("Error: Unable to resolve flow $flowName"))
                    }, {
                        Right(it)
                    })
                }, {
                    Right(it)
                })
            */
            is Either.Left -> {
                result = resolveUpdateOwnerFlow(flowName, listOf(assetStatePointer))
                when (result) {
                    is Either.Left -> {
                        println("${result.a.message}")
                        Left(Error("${result.a.message}"))
                    }
                    is Either.Right -> {
                        Right(result.b)
                    }
                }
            }
            is Either.Right -> {
                Right(result.b)
            }
        }
    } catch (e: Exception) {
        println("${e.message}")
        Left(Error("${e.message}"))
    }
}

/**
 * The ClaimAssetHTLC flow is used to claim a locked asset using HTLC.
 *
 * @property linearId The unique identifier for an AssetExchangeHTLCState.
 * @property assetClaim The [AssetLocks.AssetClaimHTLC] containing hash preImage.
 */
object ClaimAssetHTLC {
    @InitiatingFlow
    @StartableByRPC
    class Initiator
    @JvmOverloads
    constructor(
            val contractId: String,
            val claimInfo: AssetClaimHTLCData,
            val assetStateCreateCommand: CommandData,
            val updateOwnerFlow: String,
            val issuer: Party,
            val observers: List<Party> = listOf<Party>(),
            val coOwners: List<Party> = listOf<Party>()
    ) : FlowLogic<Either<Error, SignedTransaction>>() {
        /**
         * The call() method captures the logic to claim the asset by revealing preimage.
         *
         * @return Returns SignedTransaction.
         */
        @Suspendable
        override fun call(): Either<Error, SignedTransaction> = try {
            val linearId = getLinearIdFromString(contractId)
            subFlow(GetAssetExchangeHTLCStateById(contractId)).fold({
                println("AssetExchangeHTLCState for Id: ${linearId} not found.")
                Left(Error("AssetExchangeHTLCState for Id: ${linearId} not found."))            
            }, {
                val inputState = it
                val assetExchangeHTLCState = inputState.state.data
                if (!assetExchangeHTLCState.recipients.contains(ourIdentity)) {
                    println("Error: Only recipient can call claim.")
                    Left(Error("Error: Only recipient can call claim"))        
                } else {
                    println("Party: ${ourIdentity} ClaimAssetHTLC: ${assetExchangeHTLCState}")
                    val notary = inputState.state.notary
                    var participantKeys: List<PublicKey> = listOf<PublicKey>()
                    for (member in assetExchangeHTLCState.recipients) {
                        participantKeys += member.owningKey
                    }

                    val claimCmd = Command(AssetExchangeHTLCStateContract.Commands.Claim(claimInfo), participantKeys)

                    var requiredSigners: List<PublicKey> = listOf<PublicKey>()
                    for (member in assetExchangeHTLCState.recipients) {
                        requiredSigners += member.owningKey
                    }
                    requiredSigners += issuer.owningKey

                    var isTxSubmittedByRecipientCoOwner: Boolean = false
                    assetExchangeHTLCState.recipients.forEach {
                        val member = it
                        if (member.equals(ourIdentity)) {
                            // transaction should be submitted by one of the recipient co-owners only
                            isTxSubmittedByRecipientCoOwner = true
                        }
                    }
                    if ((assetExchangeHTLCState.recipients.size > 0) && (!isTxSubmittedByRecipientCoOwner)) {
                        println("Transaction submitter ($ourIdentity) has to be one of the recipient co-owners")
                        throw Exception("Transaction submitter ($ourIdentity) has to be one of the recipient co-owners")
                    }
                    
                    val assetCreateCmd = Command(assetStateCreateCommand, requiredSigners)
                    
                    val assetStateContractId = assetExchangeHTLCState.assetStatePointer.resolve(serviceHub).state.contract
                    
                    tryResolveUpdateOwnerFlowAlternatives(updateOwnerFlow, assetExchangeHTLCState.assetStatePointer,
                                assetExchangeHTLCState.recipients).fold({
                        println("Error: Unable to resolve Update Owner Flow.\n")
                        Left(Error("Error: Unable to resolve Update Owner Flow"))
                    }, {
                        println("Resolved Update owner flow to ${it}")
                        val newAssetState = subFlow(it)
                                    
                        val txBuilder = TransactionBuilder(notary)
                                .addInputState(inputState)
                                .addOutputState(newAssetState, assetStateContractId)
                                .addCommand(claimCmd)
                                .addCommand(assetCreateCmd)
                                .setTimeWindow(TimeWindow.untilOnly(assetExchangeHTLCState.lockInfo.expiryTime))
                        
                        // Verify and collect signatures on the transaction        
                        txBuilder.verify(serviceHub)
                        val partSignedTx = serviceHub.signInitialTransaction(txBuilder)
                        println("Recipient signed transaction.")

                        var sessions = listOf<FlowSession>()
                        if (!assetExchangeHTLCState.recipients.contains(issuer)) {
                            val issuerSession = initiateFlow(issuer)
                            issuerSession.send(ResponderRole.ISSUER)
                            sessions += issuerSession
                        }

                        // Add a session for each recipeint co-owner other than the submitter, for the shared assets
                        assetExchangeHTLCState.recipients.forEach {
                            if (!ourIdentity.equals(it)) {
                                val recipientCoOwnerSession = initiateFlow(it)
                                recipientCoOwnerSession.send(ResponderRole.RECIPIENT)
                                sessions += recipientCoOwnerSession
                            }
                        }

                        val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, sessions))

                        var observerSessions = listOf<FlowSession>()
                        // Add a session for each locker co-owner other than the submitter, for the shared assets
                        assetExchangeHTLCState.lockers.forEach {
                            if (!ourIdentity.equals(it)) {
                                val lockerCoOwnerSession = initiateFlow(it)
                                lockerCoOwnerSession.send(ResponderRole.LOCKER)
                                observerSessions += lockerCoOwnerSession
                            }
                        }
                        
                        for (obs in observers) {
                            val obsSession = initiateFlow(obs)
                            obsSession.send(ResponderRole.OBSERVER)
                            observerSessions += obsSession
                        }
                        Right(subFlow(FinalityFlow(fullySignedTx, sessions + observerSessions)))
                    })
                }
            })
        } catch (e: Exception) {
            println("Error claiming: ${e.message}\n")
            Left(Error("Failed to claim: ${e.message}"))
        }   
    }
    
    @InitiatedBy(Initiator::class)
    class Acceptor(val session: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val role = session.receive<ResponderRole>().unwrap { it }
            if (role == ResponderRole.ISSUER) {
                val signTransactionFlow = object : SignTransactionFlow(session) {
                    override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    }
                }
                try {
                    val txId = subFlow(signTransactionFlow).id
                    println("Issuer signed transaction.")
                    return subFlow(ReceiveFinalityFlow(session, expectedTxId = txId))
                } catch (e: Exception) {
                    println("Error signing claim asset transaction by issuer: ${e.message}\n")
                    return subFlow(ReceiveFinalityFlow(session))
                }
            } else if (role == ResponderRole.LOCKER) {
                val sTx = subFlow(ReceiveFinalityFlow(session))
                
                // Record LinearId -> TxId Mapping
                val lTx = sTx.tx.toLedgerTransaction(serviceHub)
                val htlcState = lTx.inputs[0].state.data as AssetExchangeHTLCState
                val txState = AssetExchangeTxState(sTx.id, ourIdentity, htlcState.linearId)
                val txBuilder = TransactionBuilder(sTx.notary)
                        .addOutputState(txState, AssetExchangeTxStateContract.ID)
                        .addCommand(Command(AssetExchangeTxStateContract.Commands.SaveClaimTx(), ourIdentity.owningKey))
                txBuilder.verify(serviceHub)
                val sTx2 = serviceHub.signInitialTransaction(txBuilder)
                val storedTxStateTx = subFlow(FinalityFlow(sTx2, listOf<FlowSession>()))
                
                println("Locker Received Tx: ${sTx} and recorded relevant states.")
                println("Stored LinearId -> TxID mappign with Tx: ${storedTxStateTx}.")
                return sTx
            } else if (role == ResponderRole.RECIPIENT) {
                val signTransactionFlow = object : SignTransactionFlow(session) {
                    override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    }
                }
                try {
                    val txId = subFlow(signTransactionFlow).id
                    println("Recipient signed claim asset transaction.")
                    return subFlow(ReceiveFinalityFlow(session, expectedTxId = txId))
                } catch (e: Exception) {
                    println("Error signing claim asset transaction by recipeint ($ourIdentity): ${e.message}\n")
                    return subFlow(ReceiveFinalityFlow(session))
                }
            } else if (role == ResponderRole.OBSERVER) {
                val sTx = subFlow(ReceiveFinalityFlow(session, statesToRecord = StatesToRecord.ALL_VISIBLE))
                println("Received Tx: ${sTx} and recorded states.")
                return sTx
            } else {
                println("Incorrect Responder Role.")
                throw IllegalStateException("Incorrect Responder Role.")
            }
        }
    }
}

/**
 * The UnlockAssetHTLC flow is used to unlock a locked asset using HTLC.
 *
 * @property linearId The unique identifier for an AssetExchangeHTLCState.
 */
object UnlockAssetHTLC {
    @InitiatingFlow
    @StartableByRPC
    class Initiator
    @JvmOverloads
    constructor(
            val contractId: String,
            val assetStateCreateCommand: CommandData,
            val issuer: Party,
            val observers: List<Party> = listOf<Party>(),
            val coOwners: List<Party> = listOf<Party>()
    ) : FlowLogic<Either<Error, SignedTransaction>>() {
        /**
         * The call() method captures the logic to unlock an asset.
         *
         * @return Returns SignedTransaction.
         */
        @Suspendable
        override fun call(): Either<Error, SignedTransaction> = try {
            val linearId = getLinearIdFromString(contractId)
            subFlow(GetAssetExchangeHTLCStateById(contractId)).fold({
                println("AssetExchangeHTLCState for Id: ${linearId} not found.")
                Left(Error("AssetExchangeHTLCState for Id: ${linearId} not found."))            
            }, {
                val assetExchangeHTLCState = it.state.data
                println("Party: ${ourIdentity} UnlockAssetHTLC: ${assetExchangeHTLCState}")
                val notary = it.state.notary
                var participantKeys: List<PublicKey> = listOf<PublicKey>()
                for (member in assetExchangeHTLCState.lockers) {
                    participantKeys += member.owningKey
                }
                val unlockCmd = Command(AssetExchangeHTLCStateContract.Commands.Unlock(), participantKeys)
                var requiredSigners: List<PublicKey> = listOf<PublicKey>()
                for (member in assetExchangeHTLCState.lockers) {
                    requiredSigners += member.owningKey
                }
                requiredSigners += issuer.owningKey

                var isTxSubmittedByReclaimCoOwner: Boolean = false
                assetExchangeHTLCState.lockers.forEach {
                    val member = it
                    if (member.equals(ourIdentity)) {
                        // transaction should be submitted by one of the reclaim co-owners only
                        isTxSubmittedByReclaimCoOwner = true
                    }
                }
                if ((assetExchangeHTLCState.lockers.size > 0) && (!isTxSubmittedByReclaimCoOwner)) {
                    println("Transaction submitter ($ourIdentity) has to be one of the reclaim co-owners")
                    throw Exception("Transaction submitter ($ourIdentity) has to be one of the reclaim co-owners")
                }

                val assetCreateCmd = Command(assetStateCreateCommand, requiredSigners)
                
                val reclaimAssetStateAndRef = assetExchangeHTLCState.assetStatePointer.resolve(serviceHub)
                val reclaimAssetState = reclaimAssetStateAndRef.state.data
                val assetStateContractId = reclaimAssetStateAndRef.state.contract
                
                val txBuilder = TransactionBuilder(notary)
                        .addInputState(it)
                        .addOutputState(reclaimAssetState, assetStateContractId)
                        .addCommand(unlockCmd)
                        .addCommand(assetCreateCmd)
                        .setTimeWindow(TimeWindow.fromOnly(assetExchangeHTLCState.lockInfo.expiryTime.plusNanos(1)))
                        
                // Verify and collect signatures on the transaction        
                txBuilder.verify(serviceHub)
                var partSignedTx = serviceHub.signInitialTransaction(txBuilder)
                println("${ourIdentity} signed transaction.")
                
                var sessions = listOf<FlowSession>()

                if (!ourIdentity.equals(issuer)) {
                    val issuerSession = initiateFlow(issuer)
                    issuerSession.send(ResponderRole.ISSUER)
                    sessions += issuerSession
                }

                // Add a session for each reclaim co-owner other than the submitter, for the shared assets
                assetExchangeHTLCState.lockers.forEach {
                    if (!ourIdentity.equals(it)) {
                        val lockerSession = initiateFlow(it)
                        lockerSession.send(ResponderRole.LOCKER)
                        sessions += lockerSession
                    }
                }
                val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, sessions))

                var observerSessions = listOf<FlowSession>()
                assetExchangeHTLCState.recipients.forEach {
                    if (!ourIdentity.equals(it) && !issuer.equals(it)) {
                        val recipientSession = initiateFlow(it)
                        recipientSession.send(ResponderRole.RECIPIENT)
                        observerSessions += recipientSession
                    }
                }
                for (obs in observers) {
                    val obsSession = initiateFlow(obs)
                    obsSession.send(ResponderRole.OBSERVER)
                    observerSessions += obsSession
                }
                Right(subFlow(FinalityFlow(fullySignedTx, sessions + observerSessions)))
            })
        } catch (e: Exception) {
            println("Error unlocking: ${e.message}\n")
            Left(Error("Failed to unlock: ${e.message}"))
        }
    }
    
    @InitiatedBy(Initiator::class)
    class Acceptor(val session: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val role = session.receive<ResponderRole>().unwrap { it }
            if (role == ResponderRole.ISSUER) {
                val signTransactionFlow = object : SignTransactionFlow(session) {
                    override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    }
                }
                try {
                    val txId = subFlow(signTransactionFlow).id
                    println("Issuer signed transaction.")
                    return subFlow(ReceiveFinalityFlow(session, expectedTxId = txId))
                } catch (e: Exception) {
                    println("Error signing unlock asset transaction by Issuer: ${e.message}\n")
                    return subFlow(ReceiveFinalityFlow(session))
                }
            } else if (role == ResponderRole.LOCKER) {
              val signTransactionFlow = object : SignTransactionFlow(session) {
                  override fun checkTransaction(stx: SignedTransaction) = requireThat {
                      val lTx = stx.tx.toLedgerTransaction(serviceHub)
                      "The input State must be AssetExchangeHTLCState" using (lTx.inputs[0].state.data is AssetExchangeHTLCState)
                      val htlcState = lTx.inputs.single().state.data as AssetExchangeHTLCState
                      "I must be the locker" using (htlcState.lockers.contains(ourIdentity))
                  }
              }
              try {
                  val txId = subFlow(signTransactionFlow).id
                  println("Locker signed transaction.")
                  return subFlow(ReceiveFinalityFlow(session, expectedTxId = txId))
              } catch (e: Exception) {
                  println("Error signing unlock asset transaction by Locker: ${e.message}\n")
                  return subFlow(ReceiveFinalityFlow(session))
              }
            } else if (role == ResponderRole.RECIPIENT) {
                val sTx = subFlow(ReceiveFinalityFlow(session))
                println("Recipient Received Tx: ${sTx}")
                return sTx
            } else if (role == ResponderRole.OBSERVER) {
                val sTx = subFlow(ReceiveFinalityFlow(session, statesToRecord = StatesToRecord.ALL_VISIBLE))
                println("Received Tx: ${sTx}")
                return sTx
            } else {
                println("Incorrect Responder Role.")
                throw IllegalStateException("Incorrect Responder Role.")
            }
        }
    }
}

/**
 * The resolveFlow function uses reflection to construct an instance of FlowLogic from the given
 * flow name and arguments.
 *
 * @param flowName The name of the flow provided by the remote client.
 * @param flowArgs The list of arguments for the flow provided by the remote client.
 * @return Returns an Either with an instance of FlowLogic if it was resolvable, or an Error.
 */
@Suppress("UNCHECKED_CAST")
fun resolveUpdateOwnerFlow(flowName: String, flowArgs: List<Any>): Either<Error, FlowLogic<ContractState>> = try {
    println("Attempting to resolve $flowName to a Corda flow.")
    val kotlinClass = Class.forName(flowName).kotlin
    val ctor = kotlinClass.constructors.first()
    if (ctor.parameters.size != flowArgs.size) {
        println("Flow Resolution Error: wrong number of arguments supplied.\n")
        Left(Error("Flow Resolution Error: wrong number of arguments supplied"))
    } else {
        try {
            Right(ctor.call(*flowArgs.toTypedArray()) as FlowLogic<ContractState>)
        } catch (e: Exception) {
            println("Flow Resolution Error: $flowName not a flow: ${e.message}.\n")
            Left(Error("Flow Resolution Error: $flowName not a flow"))
        }
    }
} catch (e: Exception) {
    println("Flow Resolution Error: ${e.message} \n")
    Left(Error("Flow Resolution Error: ${e.message}"))
}

fun getLinearIdFromString(linearId: String): UniqueIdentifier {
    val id = linearId.split("_").toTypedArray()
    if (id.size != 2) {
        // here the id[1] denotes an UUID and id[0] denotes its hash
        println("Invalid linearId: ${linearId}.\n")
        throw IllegalStateException("Invalid linearId: ${linearId} as per HTLC or Asset-Transfer Corda protocol.\n")
    }
    return UniqueIdentifier(externalId=id[0], id = UniqueIdentifier.fromString(id[1]).id)
}
