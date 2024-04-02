SELECT
     "Drug Product Lot Number",
     TO_CHAR( MIN("DP Date of Manufacture"), 'FMMonth YYYY' ) as "Date of Manufacture",
     LISTAGG(TO_CHAR("Input DS Batch Number"), ', ')
         WITHIN GROUP ( ORDER BY "Input DS Batch Number" ) as "Drug Substance Batch(es)"
 FROM
     MIPRANS_OWNER.DELL_GLBL_DP_GENE_32P23_NG   
 WHERE
     "Product" = ? AND "Drug Product Lot Number" IN (?)
 GROUP BY
     "Drug Product Lot Number"
 ORDER BY
     "Drug Product Lot Number"
