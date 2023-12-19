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
    Boolean transpose = (props.getProperty("document.dynamic.userdefined.ddp_transpose") ?: "false").toBoolean()
    // println transpose
    def numHeaderRows = (props.getProperty("document.dynamic.userdefined.ddp_numHeaderRows") ?: "1") as int
    // println numHeaderRows
    def numKeyHeaders = (props.getProperty("document.dynamic.userdefined.ddp_numKeyHeaders") ?: "0") as int
    // println numKeyHeaders
    def numGroupByCols = (props.getProperty("document.dynamic.userdefined.ddp_numGroupByCols") ?: "0") as int
    // println numGroupByCols
    def sourcesJson = props.getProperty("document.dynamic.userdefined.ddp_Sources")
    def sources = new JsonSlurper().parseText(sourcesJson).Records[0]
    // println prettyJson(sources)
    def pivotOnColumns = sources.PivotOnColumns
    // println prettyJson(pivotOnColumns)
    def groupByColumns = sources.PivotGroupByColumns
    // println prettyJson(groupByColumns)

    def mergeRange = { rowStart, rowEnd, colStart, colEnd, mergeCols, mergeRows ->
        return [
            rowStart: rowStart,
            rowEnd: rowEnd,
            colStart: colStart,
            colEnd: colEnd,
            mergeCols: mergeCols,
            mergeRows: mergeRows
        ]
    }
    def mergeRangeArr = []
    // def groupWithNextCounter = false

    def groups = []
    def groupStart = -1
    def inGroup = false
    (0..pivotOnColumns.size()-1).each { r ->
        def configItem = pivotOnColumns[r]
        def configItemNext = pivotOnColumns[r+1]
        def configItemPrev = pivotOnColumns[r-1]
        configItem.Seq = r

        println r + " " + groupStart
        if (configItemNext?.MergeWithPrevious
            || configItem.MergeWithSelf == configItemNext?.MergeWithSelf) {
            if (groupStart == -1) groupStart = r
            inGroup = true
        }
        else {
            if (inGroup) {
                println "gs > 0"
                groups << pivotOnColumns[groupStart..r]
                groupsStart = -1
                inGroup = false
            }
            else if (configItem.MergeWithSelf) {
                println "gs = 0"
                groups << [pivotOnColumns[r]]
            }
        }

    }
    println prettyJson(groups)

    groups.each { g ->
        println g*.MergeWithSelf.unique()
        if (g*.MergeWithSelf.unique().size() == 1) {
            println "1 for whole group"
        }
        else {
            println "1 for group, 1 for each"

        }

    }



    // def groupWithNextCounter = 0
    // (0..pivotOnColumns.size()-1).each  { r ->
    //     def configItem = pivotOnColumns[r]
    //     def configItemNext = pivotOnColumns[r+1]
    //     def configItemPrev = pivotOnColumns[r-1]

    //     if (configItemNext?.MergeWithPrevious) {
    //         // groupWithNextCounter = true
    //         groupWithNextCounter++
    //     }
    //     // if (groupWithNextCounter && !configItemNext?.MergeWithPrevious){
    //     if (groupWithNextCounter > 0 && !configItemNext?.MergeWithPrevious){

    //         if (!transpose) mergeRangeArr <<
    //             mergeRange(groupWithNextCounter, r, 0, -1, false, true)
    //         // else            mergeRangeArr << mergeRange(numHeaderRows, -1, 0, r, true, false)

    //         if (configItem.MergeWithSelf
    //             && configItem.MergeWithSelf != configItemNext?.MergeWithSelf) {
    //             if (!transpose) mergeRangeArr <<
    //                 mergeRange(r, r, 0, -1, true, false)
    //             // else            mergeRangeArr << mergeRange(numHeaderRows, -1, r, r, false, true)
    //         }
    //         // groupWithNextCounter = false
    //         groupWithNextCounter = 0
    //     }

    //     else if (groupWithNextCounter == 0 && configItem.MergeWithSelf) {
    //         if (!transpose) mergeRangeArr <<
    //             mergeRange(r, r, 0, -1, true, false)
    //         // else            mergeRangeArr << mergeRange(numHeaderRows, -1, 0, r, true, false)
    //     }


    //     println "\n- $r -"
    //     println "configItem:     " + configItem
    //     // println "configItemNext: " + configItemNext
    //     println "groupWithNextCounter:  " + groupWithNextCounter
    // }

    println prettyJson(mergeRangeArr)

    // def root = new XmlSlurper().parse(is)

    /* OUTPUT */
    // String outData
    // outData = groovy.xml.XmlUtil.serialize(root).replaceFirst("\\<\\?xml(.+?)\\?\\>", "").trim() //.replaceAll(/<tr\s*?\/\s*>/,"")
    // is = new ByteArrayInputStream(outData.toString().getBytes("UTF-8"));
    dataContext.storeStream(is, props);
}

private static String prettyJson(def thing) { return JsonOutput.prettyPrint(JsonOutput.toJson(thing)) }
