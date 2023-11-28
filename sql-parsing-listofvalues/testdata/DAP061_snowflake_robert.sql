USE ROLE BTXPS_DOMAIN_ADMIN;
 
USE WAREHOUSE PFE_COMMON_WH_XS_01;
 
USE DATABASE BTXPS_NONGXP_SRC_SBX_DB;
 
USE SCHEMA PS_LABWARE_RAW;
 
SELECT DISTINCT

PC1.NAME AS PARENT_COMPOUND_NAME   ,
P1.DESCRIPTION as Product_DESC,
P1.X_PRODUCT_TYPE as Product_Type,
S1.LOT_NAME,
S1.PROTOCOL,
S1.PROTOCOL_LEVEL,
S1.CONDITION,
S1.TIMEPOINT,
TP1.DISPLAY_STRING TP_STRING,
ROUND(DECODE(TP1.NUM_MONTHS,0,TP1.INTERVAL/86400/30.4167, tp1.num_months), 2) as Num_Months_Planned,

T1.DESCRIPTION as Test_Description,
T1.REPORTED_NAME as Test_Reported_Name,

R1.NAME as R_NAME,
R1.REPORTED_NAME AS R_REPORTED_NAME_ACTUAL,
COALESCE(PS1.REPORTED_NAME, R1.REPORTED_NAME) AS R_REPORTED_NAME_LATEST,

R1.FORMATTED_ENTRY AS R_FORMATTED_ENTRY,
U1.DISPLAY_STRING as UNITS_STRING,  

PS1.DESCRIPTION as ACCEPTANCE_CRITERIA,

'Results' as "RESULTS_LABEL",
'Time (Months)' as "TIMEPOINT_LABEL",


 TO_CHAR(ROUND(s."Protocol Months On Stability", '1'), 'TM') as "Protocol Months On Stability",
s."Text Parameter Value",
  "hello",
  "aaa" as "asdf".asdf,
  "acccaa" "bbbb"."cccc",
"Batch Number" as "Lot Number",
    TO_CHAR(MIN("Stability Base Date"), 'Mon YYYY') "Stability Study Start",
    ( TO_CHAR(ROUND(MAX("Protocol Months On Stability"), 2),  'TM') || ' Months' ) "Stability Data Presented",
       S.LOGIN_BY
           S_LOGIN_BY,
       S.RECEIVED_BY
           S_RECEIVED_BY,
       S.REVIEWER
           S_REVIEWER,
       S.SAMPLE_NUMBER
           S_SAMPLE_NUMBER,
       S.PRODUCT
           S_PRODUCT,
       S.PRODUCT_VERSION
           S_PRODUCT_VERSION,
       S.PRODUCT_GRADE
           S_PRODUCT_GRADE,
       S.SPEC_TYPE
           S_SPEC_TYPE,
       S.STAGE
           S_STAGE,
       X_STAGE.DESCRIPTION
           S_X_STAGE_DESCRIPTION,
       S.SAMPLING_POINT
           S_SAMPLING_POINT,
       S.TEXT_ID
           S_TEXT_ID,
       S.RE_SAMPLE
           S_RE_SAMPLE,
       S.ORIGINAL_SAMPLE
           S_ORIGINAL_SAMPLE,
       S.ALIQUOT
           S_ALIQUOT,
       S.PARENT_ALIQUOT
           S_PARENT_ALIQUOT,
       S.DATE_REVIEWED
           S_DATE_REVIEWED,
       S.REVIEW_NOTE
           S_REVIEW_NOTE,
       S.SAMPLE_TYPE
           S_SAMPLE_TYPE,
       CASE
           WHEN S.DESCRIPTION IS NULL OR TRIM (S.DESCRIPTION) = '' THEN 'N/A'
           ELSE S.DESCRIPTION
       END
           S_DESCRIPTION,
       S.LOT
           S_LOT,
       S.LOT_NAME
           S_LOT_NAME,
       S.SAMPLE_NAME
           S_NAME,
       CASE
           WHEN X_COMPOUNDS.NAME IS NULL OR TRIM (X_COMPOUNDS.NAME) = ''
           THEN
               'N/A'
           ELSE
               X_COMPOUNDS.NAME
       END
           S_COMPOUND,
       S.TEMPLATE,
       CASE
           WHEN S.STATUS = 'U' THEN 'Unreceived'
           WHEN S.STATUS = 'I' THEN 'Incomplete'
           WHEN S.STATUS = 'P' THEN 'In-Progress'
           WHEN S.STATUS = 'C' THEN 'Complete'
           WHEN S.STATUS = 'A' THEN 'Authorized'
           WHEN S.STATUS = 'R' THEN 'Rejected'
           WHEN S.STATUS = 'X' THEN 'Cancelled'
           WHEN TRIM (S.STATUS) = '' THEN 'N/A'
           ELSE S.STATUS
       END
           S_STATUS,
       S.EXT_LINK
           S_EXT_LINK,
       S.PROJECT
           S_PROJECT,
       S.LOCATION
           S_LOCATION,
       S.TIMEPOINT
           S_TIMEPOINT,
       S.CONDITION
           S_CONDITION,
       S.FORMULATION
           S_FORMULATION,
       S.PROTOCOL
           S_PROTOCOL,
       S.PROTOCOL_LEVEL
           S_PROTOCOL_LEVEL,
       S.X_NUM_CONTAINERS
           S_X_NUM_CONTAINERS,
       S.LOGIN_DATE
           S_LOGIN_DATE,
       S.RECD_DATE
           S_RECD_DATE,
       S.BASE_DATE
           S_BASE_DATE,
       S.AVAILABLE_DATE
           S_AVAILABLE_DATE,
       S.REQUIRED_DATE
           S_REQUIRED_DATE,
       S.TARGET_DATE
           S_TARGET_DATE,
       S.DATE_COMPLETED
           S_DATE_COMPLETED,
 
       CASE
           WHEN S.PRODUCT IS NULL
           THEN
               'N/A'
           WHEN TEST.X_TEST_TYPE IS NULL OR TEST.X_TEST_TYPE = 'ADDITIONAL'
           THEN
               'N/A'
           WHEN S.IN_SPEC = 'T'
           THEN
               'True'
           WHEN S.IN_SPEC = 'F'
           THEN
               'False'
           ELSE
               'N\A'
       END
           S_IN_SPEC,
       CASE
           WHEN S.PRODUCT IS NULL THEN 'N/A'
           WHEN TEST.X_TEST_TYPE IS NULL OR S.IN_CONTROL = 'T' THEN 'N/A'
           WHEN S.IN_CONTROL = 'T' THEN 'True'
           WHEN S.IN_CONTROL = 'F' THEN 'False'
           ELSE 'N\A'
       END
           S_IN_CONTROL,
       S.STAB_PULL_DATE
           S_STAB_PULL_DATE,
       S.STAB_PULL_BY
           S_STAB_PULL_BY,
       S.PARENT_SAMPLE
           S_PARENT_SAMPLE,
       S.X_HOLD_COND
           S_X_HOLD_COND,
       CASE
           WHEN S.TEMPLATE <> 'MAT_API_GENERIC' THEN 'N/A'
           WHEN (S.SAMPLE_VOLUME = 0 OR S.SAMPLE_VOLUME IS NULL) THEN 'N/A'
           ELSE S.SAMPLE_VOLUME || ' ' || 'Container'
       END
           S_VOLUME,
       S.SAMPLE_UNITS
           S_UNIT,
       TEST.DESCRIPTION
           T_DESCRIPTION,
       TEST.TEST_NUMBER
           T_TEST_NUMBER,
       TEST.STATUS
           T_STATUS,
       TEST.ANALYSIS
           T_ANALYSIS,
       TEST.TEST_COMMENT
           T_TEST_COMMENT,
       TEST.X_TEST_TYPE
           T_TEST_TYPE,
       TEST.IN_SPEC
           T_IN_SPEC,
       TEST.IN_CONTROL
           T_IN_CONTROL,
       TEST.ORDER_NUMBER
           T_ORDER_NUMBER,
       (SELECT LISTAGG (F.FOOT_NOTE, '; ')
                   WITHIN GROUP (ORDER BY F.FOOTNOTE_ID)
          FROM X_FOOTNOTES F
         WHERE     F.FOOTNOTE_KEYID = TEST.X_FOOTNOTE_KEYID
               AND F.REMOVED = 'F'
               AND F.FOOT_NOTE IS NOT NULL)
           T_FOOTNOTES,
       RESULT.RESULT_NUMBER
           R_RESULT_NUMBER,
       RESULT.NAME
           R_NAME,
       RESULT.ORDER_NUMBER
           R_ORDER_NUMBER,
       RESULT.STATUS
           R_STATUS,
       RESULT.REPLICATE_COUNT
           R_REPLICATE_COUNT,
       RESULT.UNITS
           R_UNITS,
       RESULT.FORMATTED_ENTRY
           R_FORMATTED_ENTRY,
       RESULT.ENTRY
           R_ENTRY,
       RESULT.REPORTABLE
           R_REPORTABLE,
       RESULT.REPORTED_NAME
           R_REPORTED_NAME,
       RESULT.DATE_ENTRY
           R_DATE_ENTRY,
       RESULT.RESULT_TYPE
           R_TYPE,
       HAZARD.LABEL_TEXT,
       CASE
           WHEN (   HAZARD.LABEL_TEXT IS NOT NULL
                 OR TRIM (HAZARD.LABEL_TEXT) <> '')
           THEN
               HAZARD.LABEL_TEXT
           WHEN (HAZARD.NAME IS NOT NULL OR TRIM (HAZARD.NAME) <> '')
           THEN
               HAZARD.NAME
           ELSE
               'N/A'
       END
           HAZARD_NAME,
       LIMS_USERS.FULL_NAME,
       CASE
           WHEN LOT.SUPPLIER IS NULL OR TRIM (LOT.SUPPLIER) = '' THEN 'N/A'
           ELSE LOT.SUPPLIER
       END
           SUPPLIER,
       ANALYSIS.ANALYSIS_TYPE,
       ANALYSIS.NAME,
       UNITS.DISPLAY_STRING
  FROM "BTXPS_NONGXP_SRC_SBX_DB"."PS_LABWARE_RAW"."SAMPLE" S,
                           TEST,
                           RESULT,
                           HAZARD,
                           LIMS_USERS,
                           ANALYSIS,
                           UNITS,
                           LOT,
                           X_COMPOUNDS,
                           X_STAGE
                     WHERE     S.SAMPLE_NUMBER = TEST.SAMPLE_NUMBER(+)
                           AND TEST.TEST_NUMBER = RESULT.TEST_NUMBER(+)
                           AND S.LOT = LOT.LOT_NUMBER(+)
                           AND S.SAMPLE_NUMBER = '120906'
                           AND (S.HAZARD = HAZARD.NAME(+))
                           AND (S.LOGIN_BY = LIMS_USERS.USER_NAME(+))
                           AND (S.RECEIVED_BY = LIMS_USERS.USER_NAME(+))
                           AND (S.REVIEWER = LIMS_USERS.USER_NAME(+))
                           AND (S.STAB_PULL_BY = LIMS_USERS.USER_NAME(+))
                           AND S.X_COMPOUND_ID = X_COMPOUNDS.COMPOUND_ID(+)
                           AND TEST.ANALYSIS = ANALYSIS.NAME(+)
                           AND (RESULT.UNITS = UNITS.UNIT_CODE(+))
                           AND (   ANALYSIS.ANALYSIS_TYPE IS NULL
                                OR ANALYSIS.ANALYSIS_TYPE <> 'LIMS_SPECIFIC')
                           AND (S.STATUS IS NULL OR S.STATUS <> 'X')
                           AND (TEST.STATUS IS NULL OR TEST.STATUS <> 'X')
                           AND (RESULT.STATUS IS NULL OR RESULT.STATUS <> 'X')
                           AND S.STAGE = X_STAGE.NAME(+)
                  ORDER BY S_SAMPLE_NUMBER, T_TEST_NUMBER, R_RESULT_NUMBER;
