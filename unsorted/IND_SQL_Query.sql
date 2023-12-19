SELECT --DISTINCT
PC1.COMPOUND_ID AS PARENT_CMPD_ID,
PC1.NAME AS PARENT_COMPOUND_NAME,
PC1.DESCRIPTION AS PARENT_COMPOUND_DESC,
C1.COMPOUND_ID,
C1.NAME as Compound_Name,
C1.DESCRIPTION as COMPOUND_DESC,
C1.COMMON_NAME as COMPOUND_COMMON_NAME, 
S1.PRODUCT,
S1.PRODUCT_VERSION as PRODUCT_VER,
P1.DESCRIPTION as Product_DESC,
P1.X_PRODUCT_TYPE as Product_Type,
S1.LOT_NAME, --For Materials, this is NOT really a Pfizer Lot
L1.LOT_NUMBER, --I am not sure WHY we need this.  We dont use this that I am aware of as a Foreign Key in any table.  We use LOT_NAME
L1.PRODUCTION_DATE as LOT_PRODUCTION_DATE,
M1.TITLE as Material_Title,
S1.X_WORK_REQUEST,
S1.FORMULATION as TEST_ARTICLE,
S1.PROTOCOL,
S1.PROTOCOL_LEVEL,
S1.CONDITION,
S1.TIMEPOINT,
TP1.DISPLAY_STRING TP_STRING,
ROUND(DECODE(TP1.NUM_MONTHS,0,TP1.INTERVAL/86400/30.4167, tp1.num_months), 2) as Num_Months_Planned,
ROUND((S1.STAB_PULL_DATE - S1.BASE_DATE)/30.4167,2) as Num_Months_Actual,
S1.BASE_DATE,
S1.TARGET_DATE,
S1.STAB_PULL_DATE,
S1.SAMPLE_NUMBER as ORIGINAL_SAMPLE,
S1.STATUS as ORIGINAL_SAMPLE_STATUS,
S2.SAMPLE_NUMBER,
S2.STATUS as SAMPLE_STATUS, 
S1.SAMPLE_NAME,
S1.SAMPLE_TYPE,
S2.TEMPLATE,  
S1.STAGE, 
S1.SPEC_TYPE,

S1.LOGIN_DATE,
S1.RECD_DATE,
S1.DATE_STARTED AS Sample_Date_Started, --Original Start Date,not necessarily Start if Child Test started earlier.
S2.DATE_COMPLETED AS Sample_Date_Completed,
S1.DATE_REVIEWED as Sample_Date_Reviewed,
S1.IN_SPEC as Sample_In_Spec,
S1.IN_CONTROL as Sample_In_Control,

GREATEST(S1.CHANGED_ON, T1.CHANGED_ON, R1.CHANGED_ON, S2.CHANGED_ON) AS Last_Modification_Date,  
T1.TEST_NUMBER,
T1.STATUS as TEST_STATUS,
T1.X_TEST_TYPE,
T1.ANALYSIS, 
T1.DESCRIPTION as Test_Description,
T1.REPORTED_NAME as Test_Reported_Name,
T1.REPLICATE_COUNT as T_Rep_Count, 
     
T1.VERSION as Analysis_Ver,
T1.DATE_STARTED AS T_Start_Date,
    
RMETA.R_METHOD,
RMETA.R_DATA_REF,
RMETA.R_TEST_LOC,
RMETA.R_TEST_DATE,

R1.RESULT_NUMBER, 
R1.REPLICATE_COUNT as R_Rep_Count,
R1.STATUS as RESULT_STATUS, 
    CASE
        WHEN S1.STATUS || S2.STATUS || T1.STATUS || R1.STATUS = 'AAAA' 
        THEN 'T' 
       ELSE 'F'
    END as Full_Authorized, 

R1.NAME as R_NAME,
R1.REPORTED_NAME AS R_REPORTED_NAME_ACTUAL,
COALESCE(PS1.REPORTED_NAME, R1.REPORTED_NAME) AS R_REPORTED_NAME_LATEST,

R1.ENTRY as R_ENTRY, 
R1.FORMATTED_ENTRY AS R_FORMATTED_ENTRY,
U1.DISPLAY_STRING as UNITS_STRING,  
R1.NUMERIC_ENTRY as R_NUMERIC_ENTRY, 
R1.ENTRY_QUALIFIER as R_QUALIFIER, 
R1.IN_SPEC as R_IN_SPEC,
R1.IN_CONTROL as R_IN_CONTROL,

PS1.DESCRIPTION as ACCEPTANCE_CRITERIA,
--PS1.TARGET as Target,
--PS1.SPEC_RULE,
--PS1.MIN_VALUE,
--PS1.MAX_VALUE,


R1.ENTERED_BY as R_Entered_By, 
R1.REPORTABLE,
R1.X_EXTERNAL_REPORT,

DECODE(PS1.COMPONENT, NULL, 'NO','YES') ON_LPS



FROM 
    GLIMS_OWNER.X_COMPOUNDS C1
    
LEFT OUTER JOIN 
    GLIMS_OWNER.X_COMPOUNDS PC1 ON C1.PARENT_COMPOUND_NAME = PC1.NAME
    
INNER JOIN
    GLIMS_OWNER.SAMPLE S1 --Original Sample 
    ON C1.COMPOUND_ID = S1.X_COMPOUND_ID
    AND S1.SAMPLE_NUMBER = S1.ORIGINAL_SAMPLE
    AND S1.STATUS IN ('A', 'C', 'P', 'U', 'I')
    AND S1.GROUP_NAME <> 'TRAINING'
    AND S1.PROTOCOL IS NOT NULL --For Stability (or not)  
    
INNER JOIN
    GLIMS_OWNER.SAMPLE S2  --Sample (Parent or Child, but not Investigational/Resample)
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

LEFT OUTER JOIN 
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
    
LEFT OUTER JOIN GLIMS_OWNER.PRODUCT P1
    ON S1.PRODUCT = P1.NAME 
    AND S1.PRODUCT_VERSION = P1.VERSION

LEFT OUTER JOIN 
    GLIMS_OWNER.LOT L1 
    ON S1.LOT_NAME = L1.LOT_NAME
       
LEFT OUTER JOIN
    GLIMS_OWNER.X_MATERIAL M1
    ON S1.LOT_NAME = M1.NAME
    
LEFT OUTER JOIN
    GLIMS_OWNER.TIMEPOINT TP1
    ON S1.TIMEPOINT = TP1.NAME 

WHERE 
--C1.NAME <> 'XYZ'
(C1.NAME = ? OR PC1.NAME = ?) AND S1.LOT_NAME = ? AND S1.PROTOCOL = ? AND S1.PROTOCOL_LEVEL = ? AND S1.CONDITION = ?

 
--661  --PF-06410293 (Adalimumab)
--527 PF-05280014 (Trastuzumab)
--487 PF-05280586 Rituximab
--S1.PROTOCOL = 'PF-06801591:2-004' --Example of GMP/LPS Stability
--AND S2.SAMPLE_NUMBER = 1204776
--AND S1.PRODUCT IS NOT NULL
--'00712991-0005-001'  --Example of R&D Stability (no Product Spec)
    
ORDER BY S1.PRODUCT, S1.LOT_NAME, S2.SAMPLE_NUMBER, T1.ANALYSIS, T1.TEST_NUMBER, R1.order_number
