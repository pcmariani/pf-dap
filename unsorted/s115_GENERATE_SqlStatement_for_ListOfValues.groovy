import java.util.Properties;
import java.io.InputStream;
import com.boomi.execution.ExecutionUtil;
import groovy.json.JsonSlurper
import groovy.json.JsonOutput;

logger = ExecutionUtil.getBaseLogger();

def OFS = "|^|"  // Output Field Separater
def unionPrefixStr = "UNION_"

for( int i = 0; i < dataContext.getDataCount(); i++ ) {
    InputStream is = dataContext.getStream(i);
    Properties props = dataContext.getProperties(i);

    def userInputsJson = props.getProperty("document.dynamic.userdefined.ddp_UserInputs")
    def userInputs = new JsonSlurper().parseText(userInputsJson).Records
    // println userInputs
    def reportContentItemJson = props.getProperty("document.dynamic.userdefined.ddp_ReportContentItem")
    def reportContentItem
    if (reportContentItemJson) reportContentItem = new JsonSlurper().parseText(reportContentItemJson).Records[0]
    // println reportContentItem
    def sqlTableJson = props.getProperty("document.dynamic.userdefined.ddp_sqlTablesJson")
    def sqlTables
    if (sqlTableJson) sqlTables = new JsonSlurper().parseText(sqlTableJson)
    // sqlTables.each{ println it }

    def root = new JsonSlurper().parse(is)
    // root.Records[0].each { println it; println ""}
    def dbSystem = root.Records[0].DbSystem
    def params = root.Records[0].WhereClauseParams //.collect{ it.Column }
    // params.each {println it}
    def paramUserInputMap = root.Records[0].ParamUserInputMap
    // println paramUserInputMap
    // def fromTable = root.Records[0].FromTable
    // println fromTable
    def fromTablesArr = root.Records[0].FromTables
    // println fromTable

    def paramsArr = []
    def paramUserInputNames = []
    def paramUserInputIds = []
    def paramOperatorsArr = []
    def filters = []
    Set aliases = []

    params.each { param ->
        println param
        if (!param.SameAsPreviousInGroup) {
            // def userInput = paramUserInputMap.find{ it.ParamName == param.Column }?.UserInputId
            def userInputId = paramUserInputMap.find{ it.ParamName == param.Column }?.UserInputId
            def userInput = userInputs.find{ it.UserInputId == userInputId }
            // println userInput
            def globalVariableName = userInput?.GlobalVariableName?.replaceAll(" ","_")
            // println globalVariableName
            def globalVarValue
            if (globalVariableName) {
                globalVarValue = ExecutionUtil.getDynamicProcessProperty("DPP_" + globalVariableName)
                if (!globalVarValue) globalVarValue = ExecutionUtil.getDynamicProcessProperty("dpp_" + globalVariableName)
                if (!globalVarValue) globalVarValue = ExecutionUtil.getDynamicProcessProperty(globalVariableName)
                if (!globalVarValue) globalVarValue = reportContentItem.SampleGlobalVariables.find{it.Name.replaceAll(" ","_") == globalVariableName}?.Value
            }
            if (globalVarValue) {
                filters << [
                    Conjunction: param.Conjunction,
                    UserInputName: userInput.UserInputName,
                    ParamName: param.Column,
                    Operator: param.Operator,
                    Value: globalVarValue
                ]
            }
            else {
                paramsArr << param.Column
                paramUserInputNames << userInput?.UserInputName
                paramUserInputIds << userInput?.UserInputId
                paramOperatorsArr << param.Operator
            }
            // add to the aliases arr only if the alias isn't the same as the one in fromTable
            if (param.Column =~ /\./
                // && param.Column.replaceFirst(/\..*/,"") != fromTable.Alias)
                && !(param.Column.replaceFirst(/\..*/,"") in fromTablesArr.Alias)
                ) {
                aliases << param.Column.replaceFirst(/\..*/,"")
            }
        }
    }
    // println paramsArr
    println aliases
    // aliases.each { println it }
    // aliases.each { println sqlTables[it].join }
    // println paramUserInputNames
    // println paramOperatorsArr.contains("IN")
    // println filters

    // go through all the joins who's keys are in aliases
    // for each one extract all the aliases and add them to aliases
    // (since aliases is a Set, it will remain unique)
    // lastly, sort the aliases in the same order as the keys in SqlTables
    aliases.each { alias ->
        println "alias: " + alias
        def aliasesWithinJoins = sqlTables[alias].join.replaceFirst(/(?si).*\bON\b/,"") =~ /(?s)\w+(?=\.)/
        // println "aliasesWitinJoins:" + aliasesWithinJoins.findAll()
        aliasesWithinJoins.findAll().unique().each { aliasWithinJoin ->
            println "aliasWithinJoin: " + aliasWithinJoin

            fromTablesArr.eachWithIndex { fromTable, uIndex ->
                // println fromTable.Alias
                if (fromTablesArr.size() > 1) alias = "${unionPrefixStr}${uIndex}:${alias}"
                // if (alias != fromTable.Alias) {
                if (!(alias in fromTablesArr.Alias)) {
                    aliases << alias
                }
            }
        }
    }
    aliases = aliases.sort { (sqlTables.keySet() as ArrayList).indexOf(it) }
    // aliases.each {println sqlTables[it].join}
    println aliases

    def paramNamesFormatted = paramsArr.collect{
        if (it =~ /\./) it.replace(".",".\"").replaceAll(/$/,"\"")
        else "\"" + it + "\""
    }.join(", ")
    // println paramNamesFormatted

    def filtersFormatted = filters.collect{
        [
            it.Conjunction,
            (it.ParamName =~ /\./) ? it.ParamName.replace(".",".\"").replaceAll(/$/,"\"") : "\"$it.ParamName\"",
            it.Operator,
            "\'$it.Value\'"
        ].join(" ")
    }.join(" ").replaceAll(/\s*null\s*/,"")

    def unionedStatementsArr = []
    fromTablesArr.each { fromTable ->
        // def SqlQueryForParamValues =
        //     "SELECT DISTINCT " + paramNamesFormatted + " \n" +
        //     "FROM " + fromTable.Name + (fromTable.Alias ? " " + fromTable.Alias : "") + " \n" +
        //     ( aliases ? aliases.collect { sqlTables[it].join }.join(" \n") + " \n" : "" ) +
        //     ( filters ? "WHERE " + filtersFormatted + " \n" : "" ) +
        //     "GROUP BY " + paramNamesFormatted + " \n" +
        //     "ORDER BY " + paramNamesFormatted
        // println SqlQueryForParamValues
        unionedStatementsArr <<
            "SELECT DISTINCT " + paramNamesFormatted + " \n" +
            "FROM " + fromTable.Name + (fromTable.Alias ? " " + fromTable.Alias : "") + " \n" +
            ( aliases ? aliases.collect { sqlTables[it].join }.join(" \n") + " \n" : "" ) +
            ( filters ? "WHERE " + filtersFormatted + " \n" : "" ) +
            "GROUP BY " + paramNamesFormatted + " \n" +
            "ORDER BY " + paramNamesFormatted

    }
    def SqlQueryForParamValues = unionedStatementsArr.join("\nUNION\n")
    // println SqlQueryForParamValues

    props.setProperty("document.dynamic.userdefined.ddp_dbSystem", dbSystem)
    props.setProperty("document.dynamic.userdefined.ddp_sqlStatement", SqlQueryForParamValues)
    props.setProperty("document.dynamic.userdefined.ddp_sqlUserInputColumnNames", paramUserInputNames.join(OFS))
    props.setProperty("document.dynamic.userdefined.ddp_sqlUserInputIdsString", paramUserInputIds.join(","))
    props.setProperty("document.dynamic.userdefined.ddp_sqlParamOperatorsString", paramOperatorsArr.join(","))
    props.setProperty("document.dynamic.userdefined.ddp_sqlFilters", JsonOutput.toJson(filters))
    ExecutionUtil.setDynamicProcessProperty("DPP_MULTISELECT", (paramOperatorsArr.contains("IN") ? "true" : "false"), false);

    // root.Records[0] << [ListOfValuesSqlQuery: SqlQueryForParamValues]

    is = new ByteArrayInputStream(JsonOutput.prettyPrint(JsonOutput.toJson(root)).getBytes("UTF-8"));
    dataContext.storeStream(is, props);
}
