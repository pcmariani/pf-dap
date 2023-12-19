SELECT 
PC1.NAME AS PARENT_COMPOUND_NAME,
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

 

FROM 
    GLIMS_OWNER.X_COMPOUNDS C1

LEFT OUTER JOIN 
    GLIMS_OWNER.X_COMPOUNDS PC1 ON C1.PARENT_COMPOUND_NAME = PC1.NAME

INNER JOIN
    GLIMS_OWNER.SAMPLE S1 
    ON C1.COMPOUND_ID = S1.X_COMPOUND_ID
    AND S1.SAMPLE_NUMBER = S1.ORIGINAL_SAMPLE
    AND S1.STATUS IN ('A', 'C', 'P', 'U', 'I')
    AND S1.GROUP_NAME <> 'TRAINING'
    AND S1.PROTOCOL IS NOT NULL 

INNER JOIN
    GLIMS_OWNER.SAMPLE S2 
    ON S1.SAMPLE_NUMBER = S2.ORIGINAL_SAMPLE AND NOT(S2.SAMPLE_NUMBER <> S2.ORIGINAL_SAMPLE and S2.PARENT_ALIQUOT = 0)
    AND S2.STATUS IN ('A', 'C', 'P', 'U', 'I')     

 

INNER JOIN
    GLIMS_OWNER.TEST T1 
    ON S2.SAMPLE_NUMBER = T1.SAMPLE_NUMBER
    AND T1.STATUS IN ('A', 'C', 'P')

INNER JOIN
    (select x.test_number, 
    MAX(DECODE(instr(x.name, 'Method No. '),1,x.formatted_entry)) as R_METHOD,
    MAX(DECODE(x.name, 'Data Reference', x.formatted_entry)) as R_DATA_REF,
    MAX(DECODE(x.name, 'Test Location', x.formatted_entry)) as R_TEST_LOC,
    MAX(DECODE(x.name, 'Test Date', x.formatted_entry)) as R_TEST_DATE
    from GLIMS_OWNER.result x
    where x.status not in ('X', 'R', 'N')
    and (x.Reported_name in ('Test Date', 'Test Location', 'Data Reference') OR instr(x.name, 'Method No') = 1)
    group by x.test_number) RMETA
    ON T1.TEST_NUMBER = RMETA.TEST_NUMBER

INNER JOIN
    GLIMS_OWNER.RESULT R1 
    ON T1.TEST_NUMBER = R1.TEST_NUMBER  
    and r1.name NOT in ('Test Date', 'Test Location', 'Data Reference', 'Method No. & Version/Effective Date', 'Method No. & Version/ Effective Date')
    and r1.status not in ('X', 'R', 'N')

LEFT OUTER JOIN
    GLIMS_OWNER.UNITS U1
    ON R1.UNITS = U1.UNIT_CODE

 

INNER JOIN 
    GLIMS_OWNER.PRODUCT_SPEC PS1 
    ON S1.PRODUCT = PS1.PRODUCT

 

    AND PS1.VERSION = (SELECT MAX(X1.VERSION) FROM PRODUCT X1 WHERE S1.PRODUCT = X1.NAME AND X1.ACTIVE = 'T')

 

    AND S1.PRODUCT_GRADE = PS1.GRADE 
    AND S1.SAMPLING_POINT = PS1.SAMPLING_POINT     
    AND S1.SPEC_TYPE = PS1.SPEC_TYPE 
    AND S1.STAGE = PS1.STAGE
    AND T1.ANALYSIS = PS1.ANALYSIS 
    AND R1.NAME = PS1.COMPONENT
    AND PS1.RULE_TYPE<> 'F'
    AND PS1.X_EXTERNAL_REPORT = 'T'

INNER JOIN GLIMS_OWNER.PRODUCT P1
    ON S1.PRODUCT = P1.NAME 
    AND S1.PRODUCT_VERSION = P1.VERSION

 

LEFT OUTER JOIN 
    GLIMS_OWNER.LOT L1 
    ON S1.LOT_NAME = L1.LOT_NAME


LEFT OUTER JOIN
    GLIMS_OWNER.TIMEPOINT TP1
    ON S1.TIMEPOINT = TP1.NAME

 

WHERE

 

(C1.NAME = ? OR PC1.NAME = ?)
AND S1.LOT_NAME = ?
AND T1.X_TEST_TYPE = ?
AND S1.CONDITION = ?

ORDER BY S1.PRODUCT, S1.LOT_NAME, S2.SAMPLE_NUMBER, T1.ANALYSIS, T1.TEST_NUMBER, R1.order_number
