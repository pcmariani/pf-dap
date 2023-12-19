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

    def sqlRaw = props.getProperty("document.dynamic.userdefined.ddp_sqlStatement")
        .replaceAll(/\\n/, "\n")
        .replaceAll(/--.*/,"")
        .replaceAll(/\n+/, "\n")
    // println sqlRaw

    def sqlRawArr = sqlRaw.split(/\s*;\s+(?=SELECT)\s*/)
    // sqlRawArr.each { println it + "\n---------\n" }
    // println sqlRawArr.size()

    // Snowflake requires multiples statements, but only the last is a SELECT.
    def sqlRawPreSelect = ""
    def sqlRawWithPreSelectRemoved = ""
    if (sqlRawArr.size() > 1) {
        sqlRawPreSelect = sqlRawArr[0..-2].join() + ";"
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

    def columnsArr = []
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
            columnsArr = selectClause .split(/,(?![^(]*\))/).collect { it.trim() }.collect { 
                if (it =~ /"\s*$/) it.replaceAll(/.*\s+(.*".*".*)/,"\$1") // alias surrounded in doublequotes
                else if (it =~ /\s+/) it.replaceAll(/.*\s+/,"")           // alias not surrounded in doublequotes
                else it                                                   // no alias
            }
        }
        // println columnsArr.size()
        // columnsArr.each { println it }

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
            def tableArr = table.split(/\n*,\n*/).collect { it.trim() }
            def base = tableArr[0].split(/\./)[0..-2].join(".")
            // tableArr[0] = firstTableArr[-1]
            // println base
            def newTableArr = [tableArr[0]]
            tableArr[1..-1].each{ newTableArr << base + "." + it + " " + it }
            newTableArr.each { println it }
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
                // .replaceAll(/(?i)(\)?\s*(?:\bAND\b|\bOR\b))/, " __\$1")
                // .replaceAll(/ {2,}/,"\n")
            // println whereClause
            // def whereClauseArr = whereClause.split(/\s*__\s*/)
            def whereClauseArr = whereClause.split(/(?=\bAND\b|\bOR\b)/)
            whereClauseArr.collect{it.trim()}.each { whereParam ->
                // println whereParam
                def operatorsRegexMatcher = /=|!=|<>|>|>=|<|<=|IS\s+NOT\s+NULL|IS\s+NULL|NOT\s+IN|IN|NOT\s+LIKE|LIKE|ANY|ALL/
                def whereParamRegexMatcher = /(?xi)^
                        \s*(\))?                      #closeParens
                        \s*(\bAND\b|\bOR\b)?          #conjunction
                        \s*(\()?                      #openParens
                        \s*(.*?)                      #column
                        \s*($operatorsRegexMatcher)   #operator
                        \s*(.*?)                      #predicate
                        \s*((?<!\+)\))?               #closeParensEnd
                    \s*$/
                def (_, closeParen, conjunction, openParens, column, operator, predicate, closeParenEnd) = (whereParam =~ whereParamRegexMatcher).findAll()[0]
                // println ([closeParen, conjunction, openParens, column, operator, predicate, closeParenEnd].join(" . "))
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
    def sqlMetadataForUser = [
        Columns: columnsArr.collect{ ["Name": it] },
        Params: paramsArrForUser
    ]
    // println prettyJson(sqlMetadataForUser)
    props.setProperty("document.dynamic.userdefined.ddp_sqlMetadataJson", JsonOutput.toJson(sqlMetadataForUser))


    /* Output for feeding into the script which generates a new SQL statement to get the list of values */
    def sqlMetadataForListOfValues = [
        PreSelectSql: sqlRawPreSelect,
        Columns: columnsArr.collect{ ["Name": it] },
        Params: paramsArrForListOfValues,
        // ParamsWithValues: paramsWithValuesArr,
        FromTables: fromTablesArr,
        Tables: fromAndJoinTablesArr,
        UnionOperator: hasUnion ? (sqlRaw =~ /\bUNION(?: ALL)?\b/)[0] : null
    ]
    // println prettyJson(sqlMetadataForListOfValues)
    def root = new JsonSlurper().parse(is)
    if (root.Records) root.Records[0].SqlMetadata = sqlMetadataForListOfValues

    is = new ByteArrayInputStream(prettyJson(root).getBytes("UTF-8"));
    // is = new ByteArrayInputStream("-------".getBytes("UTF-8"));
    dataContext.storeStream(is, props);
}

private static String prettyJson(def thing) { return JsonOutput.prettyPrint(JsonOutput.toJson(thing)) }
