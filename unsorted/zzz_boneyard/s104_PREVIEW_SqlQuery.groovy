import java.util.Properties;
import java.io.InputStream;
import groovy.json.JsonSlurper
import com.boomi.execution.ExecutionUtil;
 
logger = ExecutionUtil.getBaseLogger()
 
for( int i = 0; i < dataContext.getDataCount(); i++ ) {
    InputStream is = dataContext.getStream(i);
    Properties props = dataContext.getProperties(i);
   
    def tableIndex = props.getProperty("document.dynamic.userdefined.ddp_tableIndex") as int
   
    def designerConfig = props.getProperty("document.dynamic.userdefined.ddp_designerConfig")
    def designerConfigRoot = new JsonSlurper().parseText(designerConfig)
    def authorConfig = props.getProperty("document.dynamic.userdefined.ddp_authorConfig")
    def authorConfigRoot = new JsonSlurper().parseText(authorConfig)

    // println authorConfigRoot.params.type

    designerConfigRoot.sources.findAll{it.type == "sql"}.each { source ->
        def sqlStatementId = source.id
        def sqlStatement = source.sqlStatement.replaceAll(/\r?\n/," ")
        println sqlStatement

        // sqlStatement = '''
        // SELECT DISTINCT "Assay Name", "Parameter Field Name", "Spec Description", "Spec Units" FROM MIPRANS_OWNER.DELL_STABILITY_DATA_DP WHERE "Product Name" = ? AND "Batch Number" = ? AND "Stab Storage Condition" = ? AND "Stab Storage Orientation" = ? GROUP BY "Assay Name", "Parameter Field Name", "Spec Description", "Spec Units"ORDER BY "Assay Name", "Parameter Field Name"
        // '''
        // println sqlStatement

        def sqlParamNamesArr = []
        def sqlParamValuesArr = []

        // regex: split on WHERE: case insensitive, only if it's outside double quotes, on first occurance
        def sqlStatementArr = sqlStatement.split(/(?i)\s+WHERE\s+(?=(?:[^"]*"[^"]*")*[^"]*$)/, 2)
        // println sqlStatementArr[0]

        def sqlTableName = sqlStatementArr[0].replaceAll(/(?i).*FROM\s*/,"")
        // println sqlTableName

        def sqlParamValueFilters = []
        sqlParamValueFilters = ["Product Name": "BCMA", "Batch Number": "1111111"]

        // regex: remove last occurrance of ? and everything after it
        sqlStatementArr[1].replaceFirst(/\?(?:[^?]*)$/,"").split("\\?").each { whereClause ->
            // regexes: remove inital quote and everything before; remove final quote and everything after
            def paramName = whereClause.replaceFirst(/^.*?"\s*?/,"").replaceFirst(/\s*".*$/,"")
            sqlParamNamesArr << paramName
            def paramValue = authorConfigRoot.params.tables[tableIndex][paramName].trim()
            sqlParamValuesArr << paramValue
        }
        // println sqlParamNamesArr

        def sqlParamNames = sqlParamNamesArr.join(";")
        def sqlParamNamesWithDoubleQuotes = sqlParamNamesArr.collect{"\"" + it + "\""}.join(", ")
        def sqlParamValues = sqlParamValuesArr.join(";")
        def sqlParamValuesSqlQuery =
            "SELECT DISTINCT " + sqlParamNamesWithDoubleQuotes +
            " FROM " + sqlTableName +
            ( sqlParamValueFilters ? " WHERE " + sqlParamValueFilters.collect{k,v->"\"$k\" = '$v'"}.join(" AND ") : "" ) +
            " GROUP BY " + sqlParamNamesWithDoubleQuotes +
            " ORDER BY " + sqlParamNamesWithDoubleQuotes

        // println sqlParamValuesSqlQuery
        // println sqlStatementId + "\n------"
        // println sqlStatement + "\n------"
        // println sqlParamValues + "\n\n"

        props.setProperty("document.dynamic.userdefined.ddp_@@@_sqlStatementId", sqlStatementId)
        props.setProperty("document.dynamic.userdefined.ddp_@@@_sqlStatement", sqlStatement)
        props.setProperty("document.dynamic.userdefined.ddp_@@@_sqlParamNames", sqlParamNames)
        props.setProperty("document.dynamic.userdefined.ddp_@@@_sqlParamValues", sqlParamValues)
        props.setProperty("document.dynamic.userdefined.ddp_@@@_sqlParamValuesSqlQuery", sqlParamValuesSqlQuery)

        is = new ByteArrayInputStream("****".getBytes("UTF-8"));
        dataContext.storeStream(is, props);
    }
 //
 //    authorConfigRoot.tables[tableIndex].collect { table ->
 //        table.each { sources ->
 //            sources.value.findAll{it.type == "sql"}.each { source ->
 //                def sqlStatementId = source.id
 //                def sqlStatement = designerConfigRoot.sources.find{ it.id == sqlStatementId }.sqlStatement
 //                def sqlParams = source.sqlParams
 //
 //                props.setProperty("document.dynamic.userdefined.ddp_@@@_sqlStatementId", sqlStatementId)
 //                props.setProperty("document.dynamic.userdefined.ddp_@@@_sqlStatement", sqlStatement)
 //                props.setProperty("document.dynamic.userdefined.ddp_@@@_sqlParams", sqlParams)
 // 
 //        is = new ByteArrayInputStream("****".getBytes("UTF-8"));
 //                dataContext.storeStream(is, props);
 //            }
 //        }
 //    }
}
