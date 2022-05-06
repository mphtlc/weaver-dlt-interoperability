/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.cordaSimpleApplication.state

import com.cordaSimpleApplication.contract.SharedAssetContract
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import com.google.gson.annotations.*

/**
 * The state object recording ownership of shared fungible asset by a set of co-owning parties.
 *
 * @param quantity the number of units of the shared fungible asset.
 * @param type the type of the shared fungible asset.
 * @param coOwners the parties co-owning the asset.
 */
@BelongsToContract(SharedAssetContract::class)
data class SharedAssetState(
    val quantity: Long,
    val type: String,
    val coOwners: List<Party>,
    override val linearId: UniqueIdentifier = UniqueIdentifier()) : LinearState {

    /** The public keys of the involved parties. */
    override val participants: List<AbstractParty> get() = coOwners
}

/**
 * Below JSON is used to marshal the [SharedAssetState] ledger object to external entities (e.g., Fabric network)
 */
data class SharedAssetStateJSON(
    @SerializedName("numunits")
    val quantity: Long,
    @SerializedName("type")
    val type: String,
    @SerializedName("coowners")
    val certsOfCoOwners: List<String>
)