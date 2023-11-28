import java.util.Properties;
import java.io.InputStream;
import com.boomi.execution.ExecutionUtil;
import groovy.json.JsonSlurper
import groovy.json.JsonOutput;

logger = ExecutionUtil.getBaseLogger();

def OFS = "|^|"  // Output Field Separater

for( int i = 0; i < dataContext.getDataCount(); i++ ) {
    InputStream is = dataContext.getStream(i);
    Properties props = dataContext.getProperties(i);

    /* INPUTS */

    def userInputsJson = props.getProperty("document.dynamic.userdefined.ddp_UserInputs")
    def userInputs = userInputsJson ? new JsonSlurper().parseText(userInputsJson).Records : []
    // println prettyJson(userInputs)
    def reportContentItemJson = props.getProperty("document.dynamic.userdefined.ddp_ReportContentItem")
    def reportContentItem = reportContentItemJson ? new JsonSlurper().parseText(reportContentItemJson).Records[0] : []
    // println prettyJson(reportContentItem)

    // Incoming Document is SourceSqlQuery
    def sourceSqlQueryRoot = new JsonSlurper().parse(is).Records[0]
    // println prettyJson(sourceSqlQueryRoot)
    def dbSystem = sourceSqlQueryRoot.DbSystem
    // println dbSystem
    def paramUserInputMap = sourceSqlQueryRoot.ParamUserInputMap
    // println prettyJson(paramUserInputMap)
    def sqlMetadata = sourceSqlQueryRoot.SqlMetadata
    // println prettyJson(sqlMetadata)
    def preSelectSql = sqlMetadata.PreSelectSql
    // println prettyJson(preSelectSql)
    def params = sqlMetadata.Params
    // println prettyJson(params)
    def fromTablesArr = sqlMetadata.FromTables
    // println prettyJson(fromTablesArr)
    def tablesArr = sqlMetadata.Tables
    // println prettyJson(tablesArr)
    def unionOperator = sqlMetadata.UnionOperator
    // println unionOperator
    def hasUnion = unionOperator != null
    // println hasUnion

    /* LOGIC */

    def sqlQueryForListOfValuesArr = []
    Set paramUserInputsArr = []

    fromTablesArr.eachWithIndex { fromTable, uIndex ->
        // println "\n------------------------------  UNION INDEX: $uIndex  -------------------------------"
        // println "fromTable: " + prettyJson(fromTable)
        // println "params: " + prettyJson(params)

        def tablesArrForUIndex = tablesArr.findAll { it.UnionIndex == uIndex }
        // println tablesArrForUIndex
        def paramColumnNamesArr = []
        def filters = []
        Set aliases = []

        params.findAll{ it.UnionIndex == uIndex }.each { param ->
            // println "param: " + prettyJson(param)
            if (!param.Duplicate) {
                def userInputId, userInput
                def paramValue
                if (!param.HardCodedValue) {
                    userInputId = paramUserInputMap.find{ it.ParamName == (hasUnion ? "Statement#${uIndex}_" : "") + param.Column }?.UserInputId
                    // def userInputId = paramUserInputMap.find{ it.ParamName == param.Column }?.UserInputId
                    // println userInputId
                    userInput = userInputs.find{ it.UserInputId == userInputId }
                    // println userInput
                    def globalVariableName = userInput?.GlobalVariableName?.replaceAll(" ","_")
                    // println globalVariableName
                    if (globalVariableName) {
                        paramValue = ExecutionUtil.getDynamicProcessProperty("DPP_" + globalVariableName)
                        if (!paramValue) paramValue = ExecutionUtil.getDynamicProcessProperty("dpp_" + globalVariableName)
                        if (!paramValue) paramValue = ExecutionUtil.getDynamicProcessProperty(globalVariableName)
                        if (!paramValue) paramValue = reportContentItem.SampleGlobalVariables.find{it.Name.replaceAll(" ","_") == globalVariableName}?.Value
                        if (paramValue) paramValue = "'$paramValue'"
                    }
                    paramUserInputsArr << [
                        UserInputName: userInput.UserInputName,
                        UserInputId: userInput.UserInputId,
                        Operator: param.Operator
                    ]
                    paramColumnNamesArr << param.Column
                }
                else {
                    paramValue = param.HardCodedValue
                }
                // if the param has a value add to the where clause
                if (paramValue) {
                    filters << [
                        ParamName: param.Column,
                        Operator: param.Operator,
                        Value: paramValue
                    ]
                }
                // add to the aliases arr only if the alias isn't the same as the one in fromTable
                if (param.Column =~ /\./ && param.Column.replaceFirst(/\..*/,"") != fromTable.Alias) {
                    aliases << param.Column.replaceFirst(/\..*/,"")
                }
            }
        }
        // println "paramColumnNamesArr: " + prettyJson(paramColumnNamesArr)
        // println "paramUserInputsArr: " + prettyJson(paramUserInputsArr)
        // println "filters: " + prettyJson(filters)
        // println "aliases: " + prettyJson(aliases)


        // go through all the joins whose table alias are in aliases
        // for each one extract all the aliases and add them to aliases
        // only if it's not in the from table
        // (since aliases is a Set, it will remain unique)
        Set aliasesWithinJoinsArr = []
        aliases.each { alias ->
            // println "alias: " + prettyJson(alias)
            def join = tablesArrForUIndex.find{ it.Alias == alias }.Join
            // println "join: " + join
            Set aliasesWithinJoin = (join.replaceFirst(/(?si).*\bON\b/,"") =~ /(?s)\w+(?=\.)/).findAll()
            // println "aliasesWithinJoin: " + aliasesWithinJoin
            aliasesWithinJoin.each { aliasWithinJoin ->
                if (!(aliasWithinJoin in fromTable.Alias)) {
                    // aliases << aliasWithinJoin
                    aliasesWithinJoinsArr << aliasWithinJoin
                }
            }
        }
        // lastly, sort the aliases in the same order as the keys in SqlTables
        // aliases = aliases.sort { (tablesArr.Alias as ArrayList).indexOf(it) }
        aliases = (aliases + aliasesWithinJoinsArr).sort { (tablesArrForUIndex.Alias as ArrayList).indexOf(it) }
        // println "aliases (SORTED): " + prettyJson(aliases)


        def paramColumnNamesFormatted = paramColumnNamesArr.collect{
            if (it =~ /\./) it.replace(".",".\"").replaceAll(/$/,"\"")
            else "\"" + it + "\""
        }.join(", ")
        // println paramColumnNamesFormatted

        def filtersFormatted = filters.collect{
            [
                (it.ParamName =~ /\./) ? it.ParamName.replace(".",".\"").replaceAll(/$/,"\"") : "\"$it.ParamName\"",
                it.Operator,
                "$it.Value" //"\'$it.Value\'"
            ].join(" ")
        }.join("\n   AND ").replaceAll(/\s*null\s*/,"")
        // println filtersFormatted

        /* Construct SQL Query for List of Values */

        sqlQueryForListOfValuesArr <<
            preSelectSql + " \n" +
            "SELECT DISTINCT " + paramColumnNamesFormatted + " \n" +
            "FROM " + fromTable.Table + (fromTable.Alias ? " " + fromTable.Alias : "") + " \n" +
            ( aliases ? aliases.collect { alias -> tablesArrForUIndex.find{ it.Alias == alias }.Join }.join(" \n") + " \n" : "" ) +
            ( filters ? "WHERE " + filtersFormatted + " \n" : "" ) +
            ( hasUnion || !paramColumnNamesFormatted ? "" :
             "GROUP BY " + paramColumnNamesFormatted + " \n" +
             "ORDER BY " + paramColumnNamesFormatted )
    }

    def sqlQueryForParamValues = hasUnion ? sqlQueryForListOfValuesArr.join("\n\n$unionOperator\n\n") : sqlQueryForListOfValuesArr[0]
    println sqlQueryForParamValues + "\n"
    def isMultiSelect = paramUserInputsArr.Operator.contains("IN") ? "true" : "false"
    // println "isMultiSelect: " + isMultiSelect

    // println "paramUserInputNames: " + paramUserInputsArr.UserInputName.join(OFS)
    // println "paramUserInputIds: " + paramUserInputsArr.UserInputId.join(",")
    // println "paramUserInputOperators: " + paramUserInputsArr.Operator.join(",")

    props.setProperty("document.dynamic.userdefined.ddp_dbSystem", dbSystem)
    props.setProperty("document.dynamic.userdefined.ddp_sqlStatement", sqlQueryForParamValues)
    props.setProperty("document.dynamic.userdefined.ddp_sqlUserInputColumnNames", paramUserInputsArr.UserInputName.join(OFS))
    props.setProperty("document.dynamic.userdefined.ddp_sqlUserInputIdsString", paramUserInputsArr.UserInputId.join(","))
    props.setProperty("document.dynamic.userdefined.ddp_sqlParamOperatorsString", paramUserInputsArr.Operator.join(","))
    ExecutionUtil.setDynamicProcessProperty("DPP_MULTISELECT", isMultiSelect, false);

    is = new ByteArrayInputStream(JsonOutput.prettyPrint(JsonOutput.toJson(sourceSqlQueryRoot)).getBytes("UTF-8"));
    dataContext.storeStream(is, props);
}

private static String prettyJson(def thing) { return JsonOutput.prettyPrint(JsonOutput.toJson(thing)) }
