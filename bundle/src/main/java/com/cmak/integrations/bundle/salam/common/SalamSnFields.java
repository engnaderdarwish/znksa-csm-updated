package com.cmak.integrations.bundle.salam.common;

/**
 * Field mapping for the ServiceNow import set u_incident_import.
 *
 * Source of truth: the working Remedy web-service filters/escalation that Mahesh built
 * (INTG:SALAM_PushStatusFromRemedyToSnow_Status_INProgress_CallAPI,
 *  ..._PushStatusUpdateFromRemedyToSnow_Status_CallAPI (escalation, Assigned/Resolved),
 *  ..._PushUpdateFromRemedyToSnow_WorkInfo_CallAPI).
 *
 * Key rule: the ticket is identified by u_correlation_display, filled from the
 * Zain ticket id (staging field 536870980). There is NO separate "operation" field:
 * the operation is expressed by u_state (+ the constant assignee/group values).
 * ServiceNow answers with display_value = the SALAM incident number.
 */
public final class SalamSnFields {
    private SalamSnFields() { }

    // ---- columns of u_incident_import ----
    public static final String KEY_TICKET        = "u_correlation_display";  // <- Zain ticket id (536870980)
    public static final String STATE             = "u_state";
    public static final String ASSIGNED_TO       = "u_assigned_to";
    public static final String ASSIGNMENT_GROUP  = "u_assignment_group";
    public static final String INTEGRATION_SRC   = "u_integration_source";
    public static final String WORK_NOTES        = "u_work_notes";           // <- staging 922083007
    public static final String RESOLUTION_NOTES  = "u_resolution_notes";     // <- staging 922083104
    public static final String RESOLUTION_CODE   = "u_resolution_code";
    public static final String RESOLUTION_DATE   = "u_actual_resolution_date"; // <- staging 922181001
    public static final String CONTENT_BASE64    = "u_content_base64";        // <- staging attachment 536870920 (WorkInfo)
    public static final String FILE_NAME         = "u_file_name";             // <- attachment name

    // ---- constants taken from Mahesh's Remedy mapping ----
    public static final String SOURCE_VALUE      = "zain";

    public static final String STATE_IN_PROGRESS = "In Progress";
    public static final String IN_PROGRESS_ASSIGNEE = "ZainUser";
    public static final String IN_PROGRESS_GROUP    = "MVNO Zain";

    public static final String STATE_ASSIGNED    = "Assigned";   // CONFIRM with Mahesh: his Assigned/Resolved escalation hard-codes "Resolved"
    public static final String STATE_RESOLVED    = "Resolved";
    public static final String CC_ASSIGNEE       = "MVNO CC User";
    public static final String CC_GROUP          = "MVNO-CC-Back Office";
    public static final String RESOLUTION_CODE_VALUE = "Solution provided";
}
