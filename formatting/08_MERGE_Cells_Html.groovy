import java.util.Properties;
import java.io.InputStream;
import groovy.json.JsonSlurper

for( int i = 0; i < dataContext.getDataCount(); i++ ) {
    InputStream is = dataContext.getStream(i);
    Properties props = dataContext.getProperties(i);

    def tableDefinitionJson = props.getProperty("document.dynamic.userdefined.ddp_TableDefinition")
    def tableDefinition = tableDefinitionJson ? new JsonSlurper().parseText(tableDefinitionJson).Records : []
    println "#DEBUG tableDefinition: " + tableDefinition.Name

    Boolean isPivot = (props.getProperty("document.dynamic.userdefined.ddp_isPivot") ?: "true").toBoolean()
    // println isPivot
    Boolean transpose = (props.getProperty("document.dynamic.userdefined.ddp_transpose") ?: "false").toBoolean()
    // println transpose
    def numHeaderRows = (props.getProperty("document.dynamic.userdefined.ddp_numHeaderRows") ?: "1") as int
    // println numHeaderRows
    def numKeyHeaders = (props.getProperty("document.dynamic.userdefined.ddp_numKeyHeaders") ?: "0") as int
    // println numKeyHeaders
    def numGroupByCols = (props.getProperty("document.dynamic.userdefined.ddp_numGroupByCols") ?: "0") as int
    // println numGroupByCols

    /* --- calculate merge ranges --- */

    def mergeRangeArr = []

    if (isPivot && !transpose) {
        println "isPivot && !transpose"
        // key rows to merge vertically
        mergeRangeArr << [
            rowStart: 0,
            rowEnd: numKeyHeaders-1,
            colStart: 0,
            colEnd: -1,
            mergeCols: false,
            mergeRows: true
        ]
        // first row only to merge horizontally
        mergeRangeArr << [
            rowStart: 0,
            rowEnd: 0,
            colStart: 0,
            colEnd: -1,
            mergeCols: true,
            mergeRows: false
        ]
    }

    else if (isPivot && transpose) {
        println "isPivot && transpose"

        // topleft corner only
        mergeRangeArr << [
            rowStart: 0,
            rowEnd: numHeaderRows-1,
            colStart: 0,
            colEnd: numKeyHeaders+1,
            mergeCols: true,
            mergeRows: true
        ]
        // headers rows to merge horizontally and vertically
        mergeRangeArr << [
            rowStart: 0,
            rowEnd: (tableDefinition =~ "(?i)phrd") ? numHeaderRows-2 : numHeaderRows-1,
            // rowEnd: numHeaderRows-2,
            colStart: numKeyHeaders+1,
            colEnd: -1,
            mergeCols: true,
            mergeRows: true
        ]
        // row header key columns (not including top-left corner) to merge vertically
        mergeRangeArr << [
            rowStart: numHeaderRows,
            rowEnd: -1,
            colStart: 0,
            colEnd: numKeyHeaders-1,
            mergeCols: false,
            mergeRows: true
        ]
    }
    // println mergeRangeArr

    /* --- loop through xml (pseudo-html) data --- */

    def root = new XmlSlurper().parse(is)
    root.'**'.findAll() { node -> node.name() == "table" }.eachWithIndex() { table, t ->

        /* --- loop through the range objects --- */

        mergeRangeArr.each { range ->
            def rowStart = range.rowStart
            def rowEnd   = getEndIndex(table.tr.size(), range.rowEnd)
            def colStart = range.colStart
            def colEnd   = getEndIndex(table.tr[0].children().size(), range.colEnd)
            // println "Table: " + e + "  |  Range: " + range

            /* --- dump table into 2-dimensional array --- */

            // loop backwards from bottom to top, right to left
            def tableArr = []
            table.tr[rowStart..rowEnd].reverse().each() { tr ->
                def tableRowArr = []
                tr.children()[colStart..colEnd].reverse().each() { tableCellNode ->
                    // put the value of the TH or TD into a map with key named "val"
                    def tableCellMap = [val: tableCellNode]
                    if (tableCellNode.@rowSpan != "")
                        tableCellMap.rowSpanCount = tableCellNode.@rowSpan
                    tableRowArr << tableCellMap
                }
                tableArr << tableRowArr
            }
            // tableArr.each { println it.size(); it.each { println it }; println "" }

            /* --- loop through array to determine rowSpans and colSpans --- */

            // initialize rowSpanCountsMap to hold a count of rowSpans per column
            def rowSpanCountsMap = [:]
            (0..tableArr[0].size()-1).each { c ->
                rowSpanCountsMap[c] = 1
            }

            // compare each cell with its neighbor vertically and horizontally
            (0..tableArr.size()-1).each { r ->
                def colSpanCount = 1
                (0..tableArr[r].size()-1).each { c ->
                    def cell = tableArr[r][c]
                    // calculate merging of rows (rowSpan)
                    if (range.mergeRows && r != 0) {
                        def cellFromPrevRow = tableArr[r-1][c]
                        if (cell.val == cellFromPrevRow.val) {
                            // this will add spans for all cells to be merged. We only need
                            // the span for the first cell of the group to be merged. But
                            // that's ok because the others which are not the first one
                            // will be deleted
                            rowSpanCountsMap[c]++
                            cell.rowSpanCount = rowSpanCountsMap[c]
                            cellFromPrevRow.rowDelete = true
                        }
                        else {
                            rowSpanCountsMap[c] = 1
                        }
                    }
                    // calculate merging of columns (colSpan)
                    if (range.mergeCols && c != 0) {
                        def cellFromPrevCol = tableArr[r][c-1]
                        if (cell.val == cellFromPrevCol.val) {
                            // check if we are already merging the rows for this cell.
                            // If so, don't merge the columns
                            if (!cell.rowSpanCount && !cellFromPrevCol.rowSpanCount) {
                                // same note as above regarding uneeded spans
                                colSpanCount++
                                cell.colSpanCount = colSpanCount
                                cellFromPrevCol.colDelete = true
                            }
                        }
                        else {
                            colSpanCount = 1
                        }
                    }
                }
                // println rowSpanCountsMap
            }

            /* --- for debugging (needs fixed-width font)--- */

            // // START debug
            // println "TABLE |  ROW  DELETE  SPAN |  COL  DELETE  SPAN |  NAME"
            // (0..tableArr.size()-1).each { r ->
            //     println "------|--------------------|--------------------|------"
            //     (0..tableArr[r].size()-1).each { c ->
            //         def cell = tableArr[r][c]
            //         def rStr = r.toString().length() < 2 ? r.toString() + " " : r.toString()
            //         def cStr = c.toString().length() < 2 ? c.toString() + " " : c.toString()
            //         print   "  "        +   t
            //         print   "   |  "    +   rStr
            //         print   "   "       +   (cell.rowDelete ? "true" : "    ")
            //         print   "    "      +   (cell.rowSpanCount ? cell.rowSpanCount : " ")
            //         print   "    |  "   +   cStr
            //         print   "   "       +   (cell.colDelete ? "true" : "    ")
            //         print   "    "      +   (cell.colSpanCount ? cell.colSpanCount : " ")
            //         println "    |  "   +   cell.val
            //     }
            // }
            // println ""
            // // END debug

            /* --- alter xml (pseudo-html) in place --- */

            table.tr[rowStart..rowEnd].reverse().eachWithIndex() { tr, r ->
                tr.children()[colStart..colEnd].reverse().eachWithIndex() { it, c ->
                    def cell = tableArr[r][c]
                    // println it.toString() + " : " + cell
                    if (cell.colSpanCount)
                        it.@colSpan = cell.colSpanCount.toString()
                    if (cell.colDelete)
                        it.@colDelete = "TRUE"
                    if (cell.rowSpanCount)
                        it.@rowSpan = cell.rowSpanCount.toString()
                    if (cell.rowDelete)
                        it.@rowDelete = "TRUE"
                }
            }
            table.'**'.findAll{ it.@rowDelete == "TRUE" }.each { it.replaceNode{} }
            table.'**'.findAll{ it.@colDelete == "TRUE" }.each { it.replaceNode{} }
        }

        /* --- remove empty tr's --- */

        // there's probably a way to accomplish what follows above
        table.tr.eachWithIndex { tr, c ->
            // if the tr (serialized) is empty (looke like <tr/>) remove it and adjust @rowSpan of the tr above it
            // this will happen if all the cells in the next tr are the same as all the cells in the first tr
            if (groovy.xml.XmlUtil.serialize(tr).replaceFirst("\\<\\?xml(.+?)\\?\\>", "") =~ /(?i)\s*<tr\s*\/\s*>\s*/) {
                tr.replaceNode{}
                // get at the tr above the empty one and loop through its th's or td's
                tr.parent().children()[c-1].children().each {
                    int rowspan = it.@rowSpan.toString() as int
                    if (rowspan) {
                        int adjustedRowspan = rowspan - 1
                        // if the adjustedRowspan is 1, remove it. Else keep it
                        if (adjustedRowspan == 1) it.attributes().remove('rowSpan')
                        else it.@rowSpan = adjustedRowspan.toString()
                    }
                }
            }
        }
    }

    /* OUTPUT */
    String outData
    outData = groovy.xml.XmlUtil.serialize(root).replaceFirst("\\<\\?xml(.+?)\\?\\>", "").trim() //.replaceAll(/<tr\s*?\/\s*>/,"")
    is = new ByteArrayInputStream(outData.toString().getBytes("UTF-8"));
    dataContext.storeStream(is, props);
}

def getEndIndex(numItems, endIndex) {
  if (endIndex == -1) return numItems - 1
  else return endIndex
}
