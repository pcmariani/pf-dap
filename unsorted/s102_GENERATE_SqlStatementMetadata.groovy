/*
 * This Script is the crappiest hack possible.
 * Therefore, I am very proud of it.
 *
 * It's job is to parse an sql query.
 * Parsing is the job of a parser, not the repeated flailings of regular expressions.
 *
 * There are two parts:
 *   - Parse the FROM and JOINS (if there are any)
 *   - Parse the WHERE cluase
 *
 * The parsing of the FROM and JOINS using hacky regex is possibly exusable.
 * The parsing of the WHERE clause with regex merits death by killing.
 *
 * I think the right to do this is to find/write an EBNF grammar for a WHERE clause,
 * then feed that in Antlr which will output a parser in Java (maybe Groovy, not sure).
 *
 * This can only parse a small subset of what can be expressed in a WHERE clause.
 * It cannot hanldle:
 *   - functions
 *   - nested parentheses
 *   - BETWEEN ? AND ?
 *
 */

import java.util.Properties;
import java.io.InputStream;
import groovy.json.JsonSlurper
import groovy.json.JsonOutput;
import com.boomi.execution.ExecutionUtil;
 
logger = ExecutionUtil.getBaseLogger()
def unionPrefixStr = "UNION_"

for( int i = 0; i < dataContext.getDataCount(); i++ ) {
    InputStream is = dataContext.getStream(i);
    Properties props = dataContext.getProperties(i);

    def sqlStatementRaw = props.getProperty("document.dynamic.userdefined.ddp_sqlStatement")
    sqlStatementRaw = sqlStatementRaw.replaceAll(/\\n/, "\n").replaceAll(/--.*/,"").replaceAll(/\n+/, "\n")
    println sqlStatementRaw
    def unionedSqlStatementsArr = sqlStatementRaw.split(/\s+\bUNION\b\s+/)
    // unionedSqlStatementsArr.each{ println it + "\n----------" }

    def columnsArr = []
    def whereClauseResultsArr = []
    def tablesMap = [:]
    def fromTableMap = [:]
    def fromTablesArr = []

    unionedSqlStatementsArr.eachWithIndex { sqlStatement, uIndex ->
        // println "\n------------------------------  UNION INDEX: $uIndex  -------------------------------"
        // split the entire sql by the where clause
        def sqlStatementArr = sqlStatement.split(/(?si)WHERE\s*(?!.*WHERE)/, 2)
        // println sqlStatementArr[0]


        // --- isolate the SELECT clause ---
        def selectClause = sqlStatementArr[0].replaceAll(/(?si)(.*?)\bJOIN.*/,"\$1").replaceAll(/(?si)(.*)\bFROM.*/,"\$1").replaceAll(/SELECT\s*?\n*/,"")
        // println selectClause
        // TODO: Account for column aliases with no AS which are inside double-quotes
        if (uIndex == unionedSqlStatementsArr.size()-1) {
            columnsArr = selectClause
                .split(/,(?![^(]*\))/)
                .collect {
                    it.trim()
                }
                .collect {
                    if (it =~/(?i)\bAS\b/)                     // if column contains AS
                        it.split(/(?i)\s*\bAS\b\s*/)[-1]       // split on AS and use the last element
                    else if (it =~ /\) /)                      // else if column contains a space after a )
                        it.split(/\) +/)[-1]                   // split on space ) and use the last element
                    // else if (it =~ / /)                        // else if column contains a space
                    // it.split(/ +/)[-1]                     // split on space and use the last element
                    else it                                    // else use the column as is
                }
                .collect {
                    it.replaceAll(/\s*"\s*/,"").replaceFirst(/.+\./,"")
                }
        }

        // columnsArr.each{ println it }
        // println columnsArr.size()

        def whereClause = sqlStatementArr[1]
            .replaceAll(/(?si)\s*GROUP BY.*/,"")
            .replaceAll(/(?si)\s*ORDER BY.*/,"")
        // println whereClause

        def fromTable
        def fromTableAlias

        // if the sql has a join...
        if (sqlStatement =~ /(?si)join/) {
            // --- parse the FROM clause --- */
            // separate out the FROM clause dispensing with the SELECT part
            // println sqlStatementArr[0]
            def fromClause = (sqlStatementArr[0] =~ /(?s)^.*?\s*(?:LEFT|INNER|LEFT\s+OUTER|RIGHT\s+OUTER|FULL\s+OUTER|CROSS)?\s+JOIN/).findAll().first()
                .replaceAll(/\s*(?:LEFT|INNER|LEFT\s+OUTER|RIGHT\s+OUTER|FULL\s+OUTER|CROSS)?\s+JOIN/,"")
                .replaceAll(/(?si).*FROM\s*/,"")
                .replaceAll(/(?si)as/,"")
            // println fromClause
            fromTable = fromClause.replaceAll(/(?si)\s+\S*$/, "")
            // println fromTable
            fromTableAlias = fromClause.replaceAll(/(?si)^.*\s/,"")
            // println fromTableAlias
            // if there are joins, all the tables will PROBABLY have an alias, so add the from table to the tables map along with the joins
            if (unionedSqlStatementsArr.size() > 1) fromTableAlias = "${unionPrefixStr}${uIndex}:${fromTableAlias}"
            tablesMap[fromTableAlias] = [table:fromTable]

            /* --- parse the JOINS --- */
            def joins = (sqlStatementArr[0] =~ /(?si)\b(?=(?:LEFT|INNER|LEFT\s+OUTER|RIGHT\s+OUTER|FULL\s+OUTER|CROSS)?\s+JOIN).*/).findAll().first()
            // println joins

            // Parse out the tables, aliases and join clauses
            // This picks out the first join, parses it, then removes it from the string. Repeat until there is one left.
            while ((joins =~ /JOIN/).size() > 1) {
                def firstJoinInJoins = (joins =~ /(?si)^((?:LEFT|INNER|LEFT\s+OUTER|RIGHT\s+OUTER|FULL\s+OUTER|CROSS)?\s+JOIN.*?)((?:LEFT|INNER|LEFT\s+OUTER|RIGHT\s+OUTER|FULL\s+OUTER|CROSS)?\s+JOIN.*$)/)
                // println firstJoinInJoins.findAll()*.first()[0]
                firstJoinInJoins[0].eachWithIndex { match, r ->
                    // the first index of match is the first join in the string
                    if (r == 1) {
                        def (_, table, alias) = (match =~ /(?si)(?<=JOIN\b)\s+(.*)\s+(\w+)(?=\s+ON\s+)/)[0]
                        // println table
                        // println alias
                        if (unionedSqlStatementsArr.size() > 1) alias = "${unionPrefixStr}${uIndex}:${alias}"
                        tablesMap[alias] = [join: match - ~/\s*$/, table: table]
                    }
                    // the second index of match is the string with the first join removed
                    else if (r == 2) joins = match
                }
                // println (((joins =~ /JOIN/).findAll()).size())
            }
            // same as above but for the last join in the string
            def (_, table, alias) = (joins =~ /(?si)(?<=JOIN\b)\s+(.*)\s+(\w+)(?=\s+ON\s+)/)[0]
            if (unionedSqlStatementsArr.size() > 1) alias = "${unionPrefixStr}${uIndex}:${alias}"
            tablesMap[alias] = [join: joins - ~/\s*$/, table: table]
            // tablesMap.each{println it}
        }
        // if no joins
        else {
            fromTable = sqlStatementArr[0].replaceAll(/(?si).*\bFROM\b\s*/,"").trim()
            // def fromTableArr = fromTable.split(/\s*,\s*/)
            // println fromTableArr
        }
        // println prettyJson(tablesMap)

        /* --- parse the WHERE clause --- */
        // whereClauseArr is only for testing, there will only ever be one where clause
        def whereClauseArr = []
        whereClauseArr << whereClause
        // whereClauseArr << /(C1.NAME = ? OR PC1.NAME = ?) AND C1.'Product Name' = ? AND C1.'Storage Orientation' = ?/
        // whereClauseArr << /(C1.'name' = ? OR S1."NAME" = ?) AND p1 = ? AND p2 =? AND p3= ?/
        // whereClauseArr << /(C1.NAME = ? OR PC1.NAME = ?) OR p1 = ? AND p2 =? AND p3= ?/
        // whereClauseArr << /Product = ? AND param1 IN (?)/
        // whereClauseArr << /WHERE Product = ? AND param1 = ? AND param2 = ? AND param3 = ?/
        // whereClauseArr << /WHERE 'Product Name' = ? AND (param1 = ? AND param2 = ? AND param3 = ? )/
        // whereClauseArr << /WHERE ('Product Name' = ?) AND param1 = ? AND param2 = ? AND param3 = ? AND p4 NOT IN (?)/
        // whereClauseArr << /WHERE ('Product Name' = ?) AND param1 = ? AND param2 = ? AND param3 = ? AND (p4 NOT IN (?))/
        // whereClauseArr << /(C1.NAME = ? OR PC1.NAME = ?) AND S1.LOT_NAME = ? AND S1.PROTOCOL = ? AND S1.PROTOCOL_LEVEL = ? AND S1.CONDITION = ? AND X='hello'/
        // whereClauseArr << /WHERE (R3."STATUS" != 'X') AND (T2."STATUS" != 'X') AND (S1."PROTOCOL" = ?)/
        // whereClauseArr << /WHERE R3."STATUS" != 'X' AND T2."STATUS" != 'X' AND S1."PROTOCOL" IN (?)/

        // whereClauseArr << /WHERE (R3."STATUS" != ?) AND (T2."STATUS" != ?) AND (S1."PROTOCOL" = ?)/

        // println "----------------------------------------------------------------------------"
        // whereClauseArr.each {
        //     // println it + "\n----------------------------"
        //     def whereClauseItem = it
        //         .replaceAll(/(?i)\bIN\b\s*\(\s*\?\s*\)/,"IN ?")
        //         .replaceFirst(/^\s*WHERE\s+/,"")
        //         .replaceAll(/\n/," ")
        //         .replaceAll(/(\)?\s*(?:AND|OR))/, " __\$1")
        //     // println whereClauseItem
        //     whereClauseItem.split(/\s*__\s*/).findAll{ it.contains("?")}.each { item ->
        //         // println item
        //         def (_, conjuction, column, operator) = (item =~ /(?i)\s*(?:\)\s*)?(\bAND\b|\bOR\b)?\s*(?:\(\s*)?(.*?)\s*(=|!=|<>|>|>=|<|<=||NOT IN|IN|LIKE|NOT LIKE|ANY|ALL)\s*\?\s*\)?\s*$/).findAll()[0]
        //         println "__ " + conjuction + " __ " + column + " __ " + operator
        //     }
        //     // println ""
        // }








        whereClauseArr.each{ whereClauseItem ->

            // println it + "\n----------------------------"
            whereClauseItem = whereClauseItem
                .replaceAll(/(?i)\bIN\b\s*\(\s*\?\s*\)/,"IN ?")
                .replaceFirst(/^\s*WHERE\s+/,"")
                .replaceAll(/\n/," ")
                .replaceAll(/(\)?\s*(?:AND|OR))/, " __\$1")
            // println whereClauseItem
            whereClauseItem.split(/\s*__\s*/).findAll{ it.contains("?")}.each { item ->
                // println item
                def groupCounter = 0
                def (_, conjunction, column, operator) = (item =~ /(?i)\s*(?:\)\s*)?(\bAND\b|\bOR\b)?\s*(?:\(\s*)?(.*?)\s*(=|!=|<>|>|>=|<|<=||NOT IN|IN|LIKE|NOT LIKE|ANY|ALL)\s*\?\s*\)?\s*$/).findAll()[0]
                // println "__ " + conjunction + " __ " + column + " __ " + operator
                // }
                // // println ""

                // def groupCounter = 0
                // // println "\n" + whereClauseItem + "\n----------------------------------------------------------------------------"
                // // replace IN = (?) with IN = ? - parens are a pain, but so is everything when you're not using a parser
                // whereClauseItem = whereClauseItem.replaceAll(/(?i)\bIN\b\s*\(\s*\?\s*\)/,"IN ?").replaceFirst(/^\s*WHERE\s+/,"").replaceAll(/\n/," ")
                // // split the whereClause on ? to get each where condition (item) separated
                // whereClauseItemArr = whereClauseItem.split(/\s*\?\s*/)
                // // whereClauseItemArr.each { println it }
                // // println ""
                // whereClauseItemArr.each { item ->
                //     // --- START logic to advance the groupCounter ---
                //     // if the item is a ) - would happen at the end of a parenthetical group
                //     if (item =~ /\)\s*$/) item = ""
                //     // if the item has ( at the beginnin or after AND or OR
                //     else if (item =~ /(?i)(?:^|\bAND\b|\bOR\b)\s*\(/ ) {
                //         // if it doesn't start with ( or the operator is IN/NOT IN, advanse the group
                //         if (!(item =~ /^\s*\(/) || item =~ /(?i)\bIN\b|\bNOT IN\b/) groupCounter++
                //         // print "(.... "
                //     }
                //     // if the item has ) before an AND or OR
                //     else if (item =~ /(?i)\s*\)\s*(?:\bAND\b|\bOR\b)/ ) {
                //         groupCounter++
                //         // print ").... "
                //     }
                //     // if there is no parens
                //     else if (item =~ /(?i)(?:^|\bAND\b|\bOR\b)\s*.*/) {
                //         // if the operator is IN/NOT IN, advanse the group
                //         if (item =~ /(?i)\bIN\b|\bNOT IN\b/) groupCounter++
                //         // print "a-z.. "
                //     }
                //     // else println "..... "
                //     // println item
                //     // println ""
                //     // --- END ---

                def whereClauseItemsMap = [:]
                // parse the where clause item to separate out the conjunction, column and operator
                // def condition = (item =~ /(?i)\s*(?:\)\s*)?(\bAND\b|\bOR\b)?\s*(?:\(\s*)?(.*?)\s*(=|!=|<>|>|>=|<|<=||NOT IN|IN|LIKE|NOT LIKE|ANY|ALL)$/).findAll()[0]
                // println condition
                if (item && _) {
                    // def conjunction = condition[1]
                    // def column = condition[2]
                    // def operator = condition[3]
                    def table = fromTable
                    def columnName = column
                    if (column =~ /\./) {
                        def columnArr = column.split(/\./)
                        def alias = columnArr[0]
                        columnName = columnArr[1]
                        if (unionedSqlStatementsArr.size() > 1) alias = "${unionPrefixStr}${uIndex}:${alias}"
                        table = tablesMap[alias]?.table
                    }
                    if (unionedSqlStatementsArr.size() > 1) column = "${unionPrefixStr}${uIndex}:${column}"
                    // assemble the map
                    whereClauseItemsMap << [
                        // Group: groupCounter,
                        // Conjunction: conjunction,
                        Table: table,
                        // Column: column.replaceAll(/\s*"\s*/,""),
                        ColumnName: columnName.replaceAll(/\s*"\s*/,""),
                        Operator: operator,
                        // MultiSelect: (operator =~ /\bIN\b|\bNOT IN\b/ ? true : false)
                    ]
                }
                // add the map to the array
                if (whereClauseItemsMap) whereClauseResultsArr << whereClauseItemsMap
            }
            // whereClauseResultsArr.each { println it  }

            // loop through the array to check if any items in the same group are the same
            // whereClauseResultsArr.eachWithIndex{ item, v ->
            //     if (v > 0) {
            //         def itemPrev = whereClauseResultsArr[v-1]
            //         // println "itemPrev: " + itemPrev + "    item: " + item
            //         if (item.Group == itemPrev.Group && item.Table == itemPrev.Table && itemPrev.ColumnName == item.ColumnName) {
            //         // if (item.Table == itemPrev.Table && itemPrev.ColumnName == item.ColumnName) {
            //             // add more to the map
            //             itemPrev << [SameAsNextInGroup: true]
            //             item << [SameAsPreviousInGroup: true]
            //         }
            //     }
            // }
            // whereClauseResultsArr.each { println it }
            // println "----------------------------------------------------------------------------"
        }

        // whereClauseResultsArr.each { println it }
        // tablesMap.each {println it}


        fromTableMap = [Name:fromTable, Alias:fromTableAlias]
        // println fromTableMap
        fromTablesArr << [Name:fromTable, Alias:fromTableAlias]
        // println fromTablesArr

    }
    /* --- assemble the JSON --- */
    def sqlMetadataRoot = new JsonSlurper().parseText("{}")
    sqlMetadataRoot << [Columns: columnsArr.collect{ ["Name": it] }]
    sqlMetadataRoot << [Params: whereClauseResultsArr]
    // sqlMetadataRoot << [FromTable: fromTableMap]
    sqlMetadataRoot << [FromTables: fromTablesArr]
    if (tablesMap) sqlMetadataRoot << [Tables: tablesMap]

    // println tablesMap.collect {k,v-> [alias:k, table:v.table, join:v.join]}

    // dump results into properties
    props.setProperty("document.dynamic.userdefined.ddp_sqlMetadataJson", JsonOutput.toJson(sqlMetadataRoot))
    println JsonOutput.prettyPrint(JsonOutput.toJson(sqlMetadataRoot))

    props.setProperty("document.dynamic.userdefined.ddp_sqlColumnsJson", JsonOutput.toJson(columnsArr))
    props.setProperty("document.dynamic.userdefined.ddp_sqlColumnNamesJson", JsonOutput.toJson(columnsArr.collect{ ["Name": it] }))
    props.setProperty("document.dynamic.userdefined.ddp_sqlWhereParamsJson", JsonOutput.toJson(whereClauseResultsArr))
    // props.setProperty("document.dynamic.userdefined.ddp_sqlFromTableJson", JsonOutput.toJson(fromTableMap))
    props.setProperty("document.dynamic.userdefined.ddp_sqlFromTablesJson", JsonOutput.toJson(fromTablesArr))
    if (tablesMap) props.setProperty("document.dynamic.userdefined.ddp_sqlTablesJson", JsonOutput.toJson(tablesMap))

    def root =  new JsonSlurper().parse(is)
    root.Records[0].WhereClauseParams = whereClauseResultsArr
    // root.Records[0].FromTable = fromTableMap
    root.Records[0].FromTables = fromTablesArr

    is = new ByteArrayInputStream(JsonOutput.prettyPrint(JsonOutput.toJson(root)).getBytes("UTF-8"));
    // is = new ByteArrayInputStream("-------".getBytes("UTF-8"));
    dataContext.storeStream(is, props);
}

private static String prettyJson(def thing) { return JsonOutput.prettyPrint(JsonOutput.toJson(thing)) }
