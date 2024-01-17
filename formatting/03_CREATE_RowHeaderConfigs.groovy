import java.util.Properties;
import java.io.InputStream;
import groovy.json.JsonSlurper
import groovy.json.JsonOutput;
import com.boomi.execution.ExecutionUtil;

logger = ExecutionUtil.getBaseLogger()
def IFS = /\|\^\|/  // Input Field Separater

for( int i = 0; i < dataContext.getDataCount(); i++ ) {
    InputStream is = dataContext.getStream(i);
    Properties props = dataContext.getProperties(i);

    def reportContentItemJson = props.getProperty("document.dynamic.userdefined.ddp_ReportContentItem")
    // println reportContentItemJson
    def rowHeaderConfigArr = new JsonSlurper().parseText(reportContentItemJson).Records[0].RowHeaderConfig
    // println prettyJson(rowHeaderConfigArr)
    // println rowHeaderConfigArr.size()
    def sourcesJson = props.getProperty("document.dynamic.userdefined.ddp_Sources")
    def sources = new JsonSlurper().parseText(sourcesJson).Records
    // println prettyJson(sources)

    /* LOGIC */

    def newRowHeaderConfigsArr = []
    sources.each { source ->
        Boolean isPivot = source.IsPivot
        Boolean transpose = source.Transpose
        def groupByColsArr = source.PivotGroupByColumns
        // println groupByColsArr
        def pivotOnColsArr = source.PivotOnColumns
        // println prettyJson(pivotOnColsArr)
        if (isPivot) {
            if (!transpose) {
                groupByColsArr.eachWithIndex { groupByCol, c ->
                    def rowHeaderConfigItem = rowHeaderConfigArr.find{it.RowHeaderName == groupByCol.Column}
                    newRowHeaderConfigsArr << [
                        RowHeaderName: rowHeaderConfigItem?.RowHeaderName ?: groupByCol.Column,
                        RowHeaderWidth: rowHeaderConfigItem?.RowHeaderWidth ?: 10,
                    ]
                }
            }
            else {
                pivotOnColsArr.eachWithIndex { pivotOnCol, c ->
                    def rowHeaderConfigItem = rowHeaderConfigArr.find{it.RowHeaderName == pivotOnCol.Column}
                    newRowHeaderConfigsArr << [
                        RowHeaderName: rowHeaderConfigItem?.RowHeaderName ?: pivotOnCol.Column,
                        RowHeaderWidth: rowHeaderConfigItem?.RowHeaderWidth ?: 10,
                    ]
                }
            }
        }
    }
    // println prettyJson(newRowHeaderConfigsArr)

    def requestor = props.getProperty("document.dynamic.userdefined.ddp_Requestor")
    def reportContentItem_DynamicTableId = props.getProperty("document.dynamic.userdefined.ddp_ReportContentItem_DynamicTableId")

    def newRowHeaderConfigsWrapped = [
        Requestor: requestor,
        ReportContentItem_DynamicTableId: reportContentItem_DynamicTableId,
        Records: [
            [ RowHeaderConfig: newRowHeaderConfigsArr ]
        ]
    ]
    // println prettyJson(newRowHeaderConfigsWrapped)

    props.setProperty("document.dynamic.userdefined.ddp_RowHeaderConfig", JsonOutput.toJson(newRowHeaderConfigsWrapped))

    dataContext.storeStream(is, props);
}

private static String prettyJson(def thing) { return JsonOutput.prettyPrint(JsonOutput.toJson(thing)) }
