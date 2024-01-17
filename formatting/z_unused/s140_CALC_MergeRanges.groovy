import java.util.Properties;
import java.io.InputStream;
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
// import groovy.xml.MarkupBuilder;

for( int i = 0; i < dataContext.getDataCount(); i++ ) {
    InputStream is = dataContext.getStream(i);
    Properties props = dataContext.getProperties(i);

    Boolean isPivot = (props.getProperty("document.dynamic.userdefined.ddp_isPivot") ?: "true").toBoolean()
    // println isPivot

    def numHeaderRows = (props.getProperty("document.dynamic.userdefined.ddp_numHeaderRows") ?: "1") as int
    // println numHeaderRows
    def numHeaderCols = (props.getProperty("document.dynamic.userdefined.ddp_numHeaderCols") ?: "1") as int
    // println numHeaderCols

    def sourcesJson = props.getProperty("document.dynamic.userdefined.ddp_Sources")
    def sources = new JsonSlurper().parseText(sourcesJson).Records[0]
    // println prettyJson(sources)

    def pivotOnColumns = sources.PivotOnColumns
    // println prettyJson(pivotOnColumns)
    def groupByColumns = sources.PivotGroupByColumns
    // println prettyJson(groupByColumns)

    def mergeRangeArr = []

    def mergeRange = { rowStart, rowEnd, colStart, colEnd, mergeRows, mergeCols ->
        mergeRangeArr << [
            rowStart: rowStart,
            rowEnd: rowEnd,
            colStart: colStart,
            colEnd: colEnd,
            mergeRows: mergeRows,
            mergeCols: mergeCols
        ]
    }


    // topLeft corner
    mergeRange(0, numHeaderRows-1, 0, numHeaderCols-1, true, false)

    pivotOnColumns.eachWithIndex { row, r ->
        if (row.MergeRows) {
            mergeRange(r-1, r, numHeaderCols, -1, true, false)
        }
    }

    pivotOnColumns.eachWithIndex { row, r ->
        if (row.MergeCols) {
            mergeRange(r, r, numHeaderCols, -1, false, true)
        }
    }

    groupByColumns.eachWithIndex { col, c ->
        if (col.MergeRows) {
            mergeRange(numHeaderRows, -1, c, c, true, false)
        }
    }

    groupByColumns.eachWithIndex { col, c ->
        if (col.MergeCols) {
            mergeRange(numHeaderRows, -1, c-1, c, false, true)
        }
    }

    // println prettyJson(mergeRangeArr)

    // println "mergeRangeArr size: " + mergeRangeArr.size()
    // println prettyJson(mergeRangeArr)



    /* OUTPUT */

    props.setProperty("document.dynamic.userdefined.ddp_mergeRangeArrJson", prettyJson(mergeRangeArr))

    dataContext.storeStream(is, props);
}

private static String prettyJson(def thing) { return JsonOutput.prettyPrint(JsonOutput.toJson(thing)) }
