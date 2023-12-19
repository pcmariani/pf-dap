SELECT
   
S1.PROTOCOL AS PROTOCOL,
S1.LOT_NAME AS Formulation,
X1.TITLE AS MAT_Description,
S1.PROTOCOL_LEVEL AS PROTOCOLLEVEL,
S1.CONDITION AS CONDITION1,
S1.TIMEPOINT AS TIMEPOINT,
R3.ANALYSIS,
R3.REPORTED_NAME AS REPORTEDNAME,
R3.REPORTED_VALUE AS REPORTEDVALUE
 
FROM GLIMS_OWNER.SAMPLE S1
LEFT JOIN GLIMS_OWNER.TEST T2 on S1.SAMPLE_NUMBER = T2.SAMPLE_NUMBER
LEFT JOIN GLIMS_OWNER.RESULT R3 on T2.TEST_NUMBER = R3.TEST_NUMBER
LEFT JOIN GLIMS_OWNER.X_COMPOUNDS X4 on S1.X_COMPOUND_ID = X4.COMPOUND_ID
LEFT JOIN GLIMS_OWNER.PROTOCOL P1 on S1.PROTOCOL = P1.NAME
LEFT JOIN GLIMS_OWNER.X_MATERIAL X1 on X1.NAME = S1.LOT_NAME
 
WHERE
R3.STATUS != 'X'
AND T2.STATUS != 'X'
AND S1.PROTOCOL = '00709448-0079-001'
AND R3.REPORTED_NAME IN ('Color Intensity','Clarity','Color','pH','Relative Potency','Visible Particles','Fragments')
 
UNION
 
SELECT
 
TZ1.PROTOCOL,
TZ1.TEST_ARTICLE AS FORMULATION,
M1.TITLE AS MAT_DESCRIPTION,
TZ1.LEVEL_NAME AS PROTOCOLLEVEL,
TZ1.CONDITION AS CONDITION1,
'0_TZ' AS TIMEPOINT,
R3.ANALYSIS,
R3.REPORTED_NAME AS REPORTEDNAME,
R3.REPORTED_VALUE AS REPORTEDVALUE
 
FROM GLIMS_OWNER.PROTOCOL PR1
INNER JOIN GLIMS_OWNER.TIME_ZERO TZ1 ON PR1.NAME = TZ1.PROTOCOL AND TZ1.OBJECT_CLASS = 'SAMPLE'
INNER JOIN GLIMS_OWNER.SAMPLE S1 ON TZ1.OBJECT_ID = S1.SAMPLE_NUMBER
INNER JOIN GLIMS_OWNER.TEST T2 on S1.SAMPLE_NUMBER = T2.SAMPLE_NUMBER
INNER JOIN GLIMS_OWNER.RESULT R3 on T2.TEST_NUMBER = R3.TEST_NUMBER
LEFT OUTER JOIN GLIMS_OWNER.X_MATERIAL M1 on S1.X_MATERIAL = M1.NAME
 
WHERE
 
R3.STATUS != 'X'
AND T2.STATUS != 'X'
AND TZ1.PROTOCOL = '00709448-0079-001'
AND R3.REPORTED_NAME IN ('Color Intensity','Clarity','Color','pH','Relative Potency','Visible Particles','Fragments')
 
ORDER BY
  ANALYSIS, REPORTEDNAME, FORMULATION, PROTOCOLLEVEL, TIMEPOINT, CONDITION1
