SELECT
     "Batch Number" as "Drug Substance Batch"
     , "Date of Manufacture"
     , "Production Site" as "Site of Manufacture"
     , "Scale _L_" as "Scale (L)"
     , "Purpose of Material"
 FROM
     MIPRANS_OWNER.DELL_GLBL_DS_GENE_32S441_NG
 WHERE
     "Product" = ? AND "Batch Number" IN (?)
 ORDER BY
     "Batch Number"

