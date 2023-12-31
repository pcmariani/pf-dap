SELECT
   S1."CONDITION" AS "CONDITION1",
   S1."PROTOCOL" AS "PROTOCOL", 
   S1."FORMULATION" AS "FORMULATION",
   S1."LOCATION" AS "LOCATION",
   S1."LOT_NAME" AS "LOTNAME",
   S1."SAMPLE_NUMBER" AS "SAMPLENUMBER1",
   S1."SAMPLE_NAME" AS "SAMPLENAME",
   S1."STATUS" AS "STATUS1",
   S1."SAMPLE_TYPE" AS "SAMPLETYPE",
   S1."X_COMPOUND_ID" AS "XCOMPOUNDID",
   T2."ANALYSIS" AS "ANALYSIS",
   T2."COMMON_NAME" AS "COMMONNAME1",
   T2."DESCRIPTION" AS "DESCRIPTION",
   T2."SAMPLE_NUMBER" AS "SAMPLENUMBER2",
   T2."TEST_NUMBER" AS "TESTNUMBER",
   R3."REPORTED_NAME" AS "REPORTEDNAME",
   R3."RESULT_NUMBER" AS "RESULTNUMBER",
   X4."COMPOUND_ID" AS "COMPOUNDID",
   X4."COMMON_NAME" AS "COMMONNAME2",
   R3."REPORTED_UNITS" AS "REPORTEDUNITS",
   R3."REPORTED_VALUE" AS "REPORTEDVALUE",
   S1."TIMEPOINT" AS "TIMEPOINT",
   R3."ENTRY" AS "ENTRY",
   S1."PROTOCOL" AS "PROTOCOL1",
   S1."PROTOCOL_LEVEL" AS "PROTOCOLLEVEL",
   T5."STUDY" AS "STUDY",
   T5."PROTOCOL" AS "PROTOCOL2",
   T5."LEVEL_NAME" AS "LEVELNAME",
   T5."CONDITION" AS "CONDITION2",
   T5."TEST_ARTICLE" AS "TESTARTICLE",
   T2."STATUS" AS "STATUS2",
   R3."STATUS" AS "STATUS3",
   S1."IS_TIME_ZERO" AS "ISTIMEZERO",
   X4."PARENT_COMPOUND_NAME" AS "PARENTCOMPOUNDNAME",
   S1."PARENT_SAMPLE" AS "PARENTSAMPLE",
   S1."PARENT_ALIQUOT" AS "PARENTALIQUOT",
   S1."PARENT_COMPOSITE" AS "PARENTCOMPOSITE",
   T2."TEST_LOCATION" AS "TESTLOCATION",
   S1."PROJECT" AS "PROJECT",
   S1."X_MATERIAL" AS "XMATERIAL",
   S1."X_WORK_REQUEST" AS "XWORKREQUEST",
   P1."NAME" AS "NAME",
   P1."DESCRIPTION" AS "DESCRIPTION"
FROM

   "GLIMS_OWNER"."SAMPLE" S1

LEFT JOIN "GLIMS_OWNER"."TEST" T2 on S1."SAMPLE_NUMBER" = T2."SAMPLE_NUMBER"
LEFT JOIN "GLIMS_OWNER"."TIME_ZERO" T5 on S1."SAMPLE_NUMBER" = T5."OBJECT_ID"
LEFT JOIN "GLIMS_OWNER"."RESULT" R3 on T2."TEST_NUMBER" = R3."TEST_NUMBER"
LEFT JOIN "GLIMS_OWNER"."X_COMPOUNDS" X4 on S1."X_COMPOUND_ID" = X4."COMPOUND_ID"
LEFT JOIN "GLIMS_OWNER"."PROTOCOL" P1 on S1."PROTOCOL" = P1."NAME"

WHERE
   (R3."STATUS" != 'X')
   AND (T2."STATUS" != 'X')
   AND (S1."PROTOCOL" = '00709448-0079-001')
