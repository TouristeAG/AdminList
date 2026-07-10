package com.eventmanager.app.data.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.eventmanager.app.data.utils.NanoIdGenerator

enum class AccountHolderType {
    VOLUNTEER,
    GUEST
}

enum class AccountTransferType {
    SHIFT_CREDIT,
    SHIFT_REVERSAL,
    MANUAL_ADJUSTMENT,
    POS_SALE
}

data class AccountHolderKey(
    val holderType: AccountHolderType,
    val holderId: String
) {
    fun storageKey(): String = "${holderType.name}:$holderId"
}

@Entity(
    tableName = "account_transfers",
    indices = [
        Index(value = ["transferId"], unique = true),
        Index(value = ["sourceReference"], unique = true),
        Index(value = ["sheetsId"]),
        Index(value = ["holderType", "holderId"]),
        Index(value = ["lastModified"]),
        Index(value = ["createdAt"])
    ]
)
data class AccountTransfer(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sheetsId: String? = null,
    val transferId: String = NanoIdGenerator.generateVolunteerId(),
    val holderType: AccountHolderType,
    val holderId: String,
    val holderName: String,
    val amount: Double,
    val currencyCode: String = "CHF",
    val type: AccountTransferType,
    val sourceReference: String,
    val jobReferenceKey: String = "",
    val jobTypeName: String = "",
    val jobDate: Long? = null,
    val description: String = "",
    val creditAmountPaid: Double? = null,
    val cashAmountPaid: Double? = null,
    val posBarDiscountPercent: Int? = null,
    val posItemsJson: String = "",
    val posVenueName: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val lastModified: Long = System.currentTimeMillis()
)

fun jobReferenceKey(job: Job): String =
    "${job.volunteerId}|${job.jobTypeName}|${job.date}|${job.venueName}|${job.shiftTime}"
