package `in`.codelif.ktjiit.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public data class GeneralInfo(
    @SerialName("studentname") val name: String = "",
    @SerialName("registrationno") val enrollmentNo: String = "",
    @SerialName("programcode") val program: String = "",
    val branch: String = "",
    val batch: String = "",
    @SerialName("sectioncode") val section: String = "",
    val semester: Int = 0,
    @SerialName("academicyear") val academicYear: String = "",
    @SerialName("admissionyear") val admissionYear: String = "",
    @SerialName("dateofbirth") val dateOfBirth: String = "",
    val gender: String = "",
    @SerialName("bloodgroup") val bloodGroup: String = "",
    val nationality: String = "",
    val category: String = "",
    @SerialName("apaarid") val apaarId: String = "",
    @SerialName("studentcellno") val phone: String = "",
    @SerialName("studentemailid") val collegeEmail: String = "",
    @SerialName("studentpersonalemailid") val personalEmail: String = "",
    @SerialName("fathersname") val fatherName: String = "",
    @SerialName("mothername") val motherName: String = "",
    @SerialName("parentcellno") val parentPhone: String = "",
    @SerialName("parentemailid") val parentEmail: String = "",
    @SerialName("paddress1") val permanentAddress1: String = "",
    @SerialName("paddress2") val permanentAddress2: String = "",
    @SerialName("paddress3") val permanentAddress3: String = "",
    @SerialName("pcityname") val permanentCity: String = "",
    @SerialName("pdistrict") val permanentDistrict: String = "",
    @SerialName("pstatename") val permanentState: String = "",
    @SerialName("ppostalcode") val permanentPin: String = "",
    @SerialName("caddress1") val currentAddress1: String = "",
    @SerialName("caddress3") val currentAddress3: String = "",
    @SerialName("ccityname") val currentCity: String = "",
    @SerialName("cdistrict") val currentDistrict: String = "",
    @SerialName("cstatename") val currentState: String = "",
    @SerialName("cpostalcode") val currentPin: String = "",
    @SerialName("institutecode") val institute: String = "",
)

@Serializable
public data class Qualification(
    @SerialName("qualificationcode") val code: String = "",
    @SerialName("boardname") val board: String = "",
    @SerialName("yearofpassing") val year: String = "",
    @SerialName("obtainedmarks") val obtained: Double = 0.0,
    @SerialName("fullmarks") val full: Double = 0.0,
    @SerialName("percentagemarks") val percent: Double = 0.0,
    val grade: String = "",
    val cgpa: Double = 0.0,
)

@Serializable
public data class Photo(val photo: String? = null, val signature: String? = null)

@Serializable
public data class PersonalInfo(
    @SerialName("generalinformation") val general: GeneralInfo = GeneralInfo(),
    @SerialName("qualification") val qualifications: List<Qualification> = emptyList(),
    /** base64 jpeg */
    @SerialName("photo&signature") val photo: Photo = Photo(),
)

@Serializable
public data class BankInfo(
    @SerialName("bankname") val bank: String = "",
    @SerialName("bankaccountnumber") val account: String = "",
    @SerialName("ifsccode") val ifsc: String = "",
    @SerialName("accountholdername") val holder: String = "",
    @SerialName("bankaddress") val address: String = "",
    @SerialName("bankcity") val city: String = "",
    @SerialName("bankstate") val state: String = "",
    @SerialName("bankcitypin") val pin: String = "",
    @SerialName("acholdertypeflag") val holderType: String = "",
    /** Y once the office locks it */
    @SerialName("freezed") val frozen: String = "",
)

@Serializable
internal data class BankResponse(val bankinfo: BankInfo = BankInfo())

@Serializable
public data class HostelInfo(
    @SerialName("hosteldescription") val hostel: String = "",
    @SerialName("hostelcode") val hostelCode: String = "",
    @SerialName("allotedroomno") val room: String = "",
    @SerialName("floor") val floor: String = "",
    @SerialName("beddesc") val bed: String = "",
    @SerialName("roomtype") val roomType: String = "",
    @SerialName("hosteltypedesc") val hostelType: String = "",
    @SerialName("allotedfromdate") val from: String = "",
    @SerialName("allotedtilldate") val until: String = "",
    @SerialName("hostellefton") val leftOn: String? = null,
)

@Serializable
internal data class HostelResponse(val presenthosteldetail: HostelInfo? = null)

@Serializable
public data class FeeHead(
    @SerialName("stynumber") val semester: Int = 0,
    @SerialName("academicyear") val academicYear: String = "",
    @SerialName("stytypedesc") val type: String = "",
    @SerialName("feeamount") val fee: Double = 0.0,
    @SerialName("receiveamount") val paid: Double = 0.0,
    @SerialName("dueamount") val due: Double = 0.0,
    @SerialName("waiveramount") val waived: Double = 0.0,
    @SerialName("refundamount") val refunded: Double = 0.0,
    @SerialName("currencycode") val currency: String = "INR",
    @SerialName("regallowdate") val registrationAllowedOn: String = "",
)

@Serializable
public data class AdvanceAmount(val amount: Double = 0.0, @SerialName("currencycode") val currency: String = "INR")

@Serializable
public data class FeeSummary(
    @SerialName("feeHeads") val heads: List<FeeHead> = emptyList(),
    @SerialName("advanceamount") val advance: List<AdvanceAmount> = emptyList(),
) {
    public val totalDue: Double get() = heads.sumOf { it.due }
}

@Serializable
public data class FeedbackEvent(
    @SerialName("eventid") val id: String = "",
    @SerialName("eventdescription") val description: String = "",
    @SerialName("feedbackcode") val code: String = "",
)

@Serializable
internal data class FeedbackEventsResponse(val eventList: List<FeedbackEvent> = emptyList())
