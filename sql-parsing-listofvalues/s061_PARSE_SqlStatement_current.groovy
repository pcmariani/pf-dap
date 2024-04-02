import java.util.Properties;
import java.io.InputStream;
import groovy.json.JsonSlurper
import groovy.json.JsonOutput;
import com.boomi.execution.ExecutionUtil;

logger = ExecutionUtil.getBaseLogger()
def joinsRegexMatcher = /(?:LEFT|INNER|LEFT\s+OUTER|RIGHT\s+OUTER|FULL\s+OUTER|CROSS)/

for( int i = 0; i < dataContext.getDataCount(); i++ ) {
    InputStream is = dataContext.getStream(i);
    Properties props = dataContext.getProperties(i);

    def outputs = (props.getProperty("document.dynamic.userdefined.ddp_outputs") ?: "ddp_sqlMetadataJson").split(/\s*,\s*/)
    // def outputs = props.getProperty("document.dynamic.userdefined.ddp_outputs")
    // println outputs

    def sqlRaw = props.getProperty("document.dynamic.userdefined.ddp_sqlStatement")
        .replaceAll(/\\n/, "\n")
        .replaceAll(/--.*/,"")
        .replaceAll(/\n+/, "\n")
    // println sqlRaw

    def sqlRawArr = sqlRaw.split(/\s*;\s*/)
    // sqlRawArr.each { println it + "\n---------\n" }
    // println sqlRawArr.size()

    // Snowflake requires multiples statements, but only the last is a SELECT.
    def sqlRawPreSelect = ""
    def sqlRawWithPreSelectRemoved = ""
    if (sqlRawArr.size() > 1) {
        sqlRawPreSelect = sqlRawArr[0..-2].join(";\n ") + ";"
        sqlRawWithPreSelectRemoved = sqlRawArr[-1]
    }
    else {
        sqlRawWithPreSelectRemoved = sqlRawArr[0]
    }
    // println "sqlRawPreSelect: " + sqlRawPreSelect
    // println "sqlRawWithPreSelectRemoved: " + sqlRawWithPreSelectRemoved

    def unionedSqlStatementsArr = sqlRawWithPreSelectRemoved.split(/\s+\bUNION(?: ALL)?\b\s+/)
    // unionedSqlStatementsArr.each{ println it + "\n----------" }
    def hasUnion = unionedSqlStatementsArr.size() > 1
    // println hasUnion

    def columnsMap = []
    def fromTablesArr = []
    def fromAndJoinTablesArr = []
    def paramsArrForListOfValues = []
    def paramsArrForUser = []
    // def paramsWithValuesArr = []

    unionedSqlStatementsArr.eachWithIndex { sqlStatement, uIndex ->
        // println "\n------------------------------  UNION INDEX: $uIndex  -------------------------------"

        // split the entire sql by the where clause
        def sqlStatementArr = sqlStatement.split(/(?si)WHERE\s*(?!.*WHERE)/, 2)
        // println sqlStatementArr[0]


        /*
         * --- parse the COLUMNS ---
         */
        def selectClause = sqlStatementArr[0]
            .replaceAll(/(?si)(.*?)\bJOIN.*/,"\$1")
            .replaceAll(/(?si)(.*)\bFROM.*/,"\$1")
            .replaceAll(/(?i)SELECT(?: DISTINCT)?\s*?\n*/,"")
        // println selectClause

        if (uIndex == unionedSqlStatementsArr.size()-1) {
            columnsMap = selectClause.split(/(?s),(?![^(]*\))/)
                .collect {
                    it.trim().replaceAll(/\s*\n+\s*/," ")
                }
                .collectEntries {
                    String columnExp
                    String colAlias
                    // alias, surrounded in doublequotes
                    if (it =~ /\s+.*".*".*\s*$/) {
                        (_, columnExp, colAlias) = (it =~ /(.*)\s+(.*".*".*)\s*/)[0]
                        columnExp = columnExp.replaceFirst(/(?i)\s+as\s*$/, "")
                    }
                    // no alias, surrounded in doublequotes
                    else if (it =~ /"/) {
                        columnExp = it
                        colAlias = it
                    }
                    // alias, no quotes
                    else if (it =~ /\s+/) {
                        (_, columnExp, colAlias) = (it =~ /(.*)\s+(.*)/)[0]
                        columnExp = columnExp.replaceFirst(/(?i)\s+as\s*$/, "")
                    }
                    // no alias, no quotes
                    else {
                        columnExp = it
                        colAlias = it
                    }
                    // remove doublequotes and table prefix
                    // these will need to match with column names return from the db query
                    // which don't have them
                    columnExp = columnExp.replaceAll(/\s*"\s*/,"").replaceFirst(/.+\./,"")
                    colAlias = colAlias.replaceAll(/\s*"\s*/,"").replaceFirst(/.+\./,"")
                    // key is the column expression - the thing which is not the alias
                    // value is the column alias
                    // if there is no alias, the key and value will be the same
                    [(columnExp): colAlias]
                }
        }
        // println columnsMap.size()
        columnsMap.each { k,v -> println k + "  :::  " + v + "\n" }

        /*
         * --- parse the FROM clause ---
         */
        def fromTableMap = [:]
        def setFromTableMap = { alias, table ->
            [
                UnionIndex: uIndex,
                Alias: alias,
                Table: table,
            ]
        }
        if (sqlStatement =~ /(?si)join/) {
            // separate out the FROM clause dispensing with the SELECT part
            def fromClause = (sqlStatementArr[0] =~ /(?s)^.*?\s*$joinsRegexMatcher?\s+JOIN/).findAll().first()
                .replaceAll(/\s*$joinsRegexMatcher?\s+JOIN/,"")
                .replaceAll(/(?si).*FROM\s*/,"")
                .replaceAll(/(?si)as/,"")
            // println fromClause
            def alias = fromClause.replaceAll(/(?si)^.*\s/,"")
            def table = fromClause.replaceAll(/(?si)\s+\S*$/, "")
            fromTableMap = setFromTableMap( alias, table)
        }
        else {
            def table = sqlStatementArr[0].replaceAll(/(?si).*\bFROM\b\s*/,"").trim()
            fromTableMap = setFromTableMap( null, table)
        }
        fromTablesArr << fromTableMap
        fromAndJoinTablesArr << fromTableMap
        // println prettyJson(fromTablesArr)


        /*
         * --- parse the JOINS ---
         */
        def setJoinMap = { alias, table, join ->
            [
                UnionIndex: uIndex,
                Alias: alias,
                Table: table,
                Join: join.trim()
            ]
        }
        if (sqlStatement =~ /(?si)join/) {
            def joinsStr = (sqlStatementArr[0] =~ /(?si)\b(?=$joinsRegexMatcher?\s+JOIN).*/).findAll().first()
            // println joinsStr
            // Parse out the tables, aliases and join clauses
            // This picks out the first join, parses it, then removes it from the string. Repeat until there is one left.
            while ((joinsStr =~ /JOIN/).size() > 1) {
                def (_, firstJoin, restOfJoins) = (joinsStr =~ /(?si)^($joinsRegexMatcher?\s+JOIN.*?)($joinsRegexMatcher?\s+JOIN.*$)/)[0]
                def (__, table, alias) = (firstJoin =~ /(?si)(?<=JOIN\b)\s+(.*)\s+(\w+)(?=\s+ON\s+)/)[0]
                fromAndJoinTablesArr << setJoinMap(alias, table, firstJoin)
                joinsStr = restOfJoins
            } // same as above but for the last join in the string
            def (_, table, alias) = (joinsStr =~ /(?si)(?<=JOIN\b)\s+(.*)\s+(\w+)(?=\s+ON\s+)/)[0]
            fromAndJoinTablesArr << setJoinMap(alias, table, joinsStr)
        }
        // println prettyJson(fromAndJoinTablesArr)


        /*
         * --- parse params from the WHERE clause ---
         */
        if (sqlStatementArr.size() > 1) {
            def whereClause = sqlStatementArr[1]
                .replaceAll(/(?si)\s*GROUP BY.*/,"")
                .replaceAll(/(?si)\s*ORDER BY.*/,"")
                .replaceAll(/(?i)\bIN\b\s*\(\s*\?\s*\)/,"IN ?")
                .replaceFirst(/^\s*WHERE\s+/,"")
                .replaceAll(/\n/," ")
                .replaceAll(/(?i)(\)?\s*(?:\bAND\b|\bOR\b))/, " __\$1")
            // println whereClause
            def whereClauseArr = whereClause.split(/\s*__\s*/)
            // println prettyJson(whereClauseArr)
            whereClauseArr.each { whereParam ->
                // if (whereParam.contains("NOT IN")) println "\n" + whereParam
                def whereParamRegexMatcher = /(?xi)^
                        \s*(\))?                                                          #closeParens
                        \s*(\bAND\b|\bOR\b)?                                              #conjunction
                        \s*(\()?                                                          #openParens
                        \s*(.*?)                                                          #column
                        \s*(=|!=|<>|>|>=|<|<=|NOT\s+IN|IN|LIKE|NOT\s+LIKE|ANY|ALL)        #operator
                        \s*(.*?)                                                          #predicate
                    \s*$/
                def (_, closeParen, conjunction, openParens, column, operator, predicate) = (whereParam =~ whereParamRegexMatcher).findAll()[0]
                // println ([closeParen, conjunction, openParens, column, operator, predicate].join(" | "))
                def table
                if (column =~ /\./) {
                    def alias = column.replaceFirst(/\..*$/,"")
                    table = fromAndJoinTablesArr.find{ it.UnionIndex == uIndex && it.Alias == alias }?.Table
                } else {
                    table = fromTablesArr[uIndex].Table
                }
                //
                def whereParamMap = [
                    UnionIndex: uIndex,
                    Table: table,
                    Column: column.replaceAll(/\s*"\s*/,""),
                    Operator: operator,
                    // HardCodedValue: !predicate.contains("?") ? predicate : null
                    HardCodedValue: predicate != "?" ? predicate : null
                ]
                // Very rarely there will be params which have different aliases but theose aliases refer to the same table, and which have the same column name
                if (paramsArrForListOfValues.find{ it.UnionIndex == uIndex && it.Table == table && it.Column.replaceFirst(/^.*?\./,"") == column.replaceFirst(/^.*?\./,"") }) {
                    whereParamMap << [
                        Duplicate: true
                    ]
                }
                paramsArrForListOfValues << whereParamMap
                // A different map for display in flow and which will be used for saving to the Db
                // def paramsArrForUser = []
                // if (predicate.contains("?")) {
                if (predicate == "?") {
                    paramsArrForUser << [
                        Table: table,
                        Column: (hasUnion ? "Statement#${uIndex}_" : "") + column.replaceAll(/\s*"\s*/,""),
                        Operator: operator,
                    ]
                    // paramsArrForUser << whereParamMapForUser
                }
            }
        }
    }
    // println prettyJson(paramsArrForListOfValues)


    /*
     * --- OUTPUT ---
     */

    /* Output for display in Flow when attaching UserInputs to Params, and for selecting Columns for pivot config */
    if ("ddp_sqlMetadataJson" in outputs) {
        def sqlMetadataForUser = [
            // Columns: columnsMap,
            Columns: columnsMap.collect{ k,v -> ["Name": v] },
            Params: paramsArrForUser
        ]
        // println prettyJson(sqlMetadataForUser)
        props.setProperty("document.dynamic.userdefined.ddp_sqlMetadataJson", JsonOutput.toJson(sqlMetadataForUser))
    }

    if ("ddp_sqlColumnsMap" in outputs) {
        props.setProperty("document.dynamic.userdefined.ddp_sqlColumnsMap", JsonOutput.toJson(columnsMap))
    }


    if ("addSqlMetadataToSourceSqlQuery" in outputs) {
        def root = new JsonSlurper().parse(is)
        /* Output for feeding into the script which generates a new SQL statement to get the list of values */
        def sqlMetadataForListOfValues = [
            PreSelectSql: sqlRawPreSelect,
            // Columns: columnsMap.collect{ ["Name": it] },
            Columns: columnsMap.collect{ it },
            Params: paramsArrForListOfValues,
            // ParamsWithValues: paramsWithValuesArr,
            FromTables: fromTablesArr,
            Tables: fromAndJoinTablesArr,
            UnionOperator: hasUnion ? (sqlRaw =~ /\bUNION(?: ALL)?\b/)[0] : null
        ]
        // println prettyJson(sqlMetadataForListOfValues)
        if (root.Records) root.Records[0].SqlMetadata = sqlMetadataForListOfValues
        is = new ByteArrayInputStream(prettyJson(root).getBytes("UTF-8"));
    }

    // is = new ByteArrayInputStream("-------".getBytes("UTF-8"));
    dataContext.storeStream(is, props);
}

private static String prettyJson(def thing) { return JsonOutput.prettyPrint(JsonOutput.toJson(thing)) }

