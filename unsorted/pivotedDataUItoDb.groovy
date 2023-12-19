import java.util.Properties;
import java.io.InputStream;
import com.boomi.execution.ExecutionUtil;
import groovy.json.JsonSlurper
import groovy.json.JsonOutput;

logger = ExecutionUtil.getBaseLogger();

for( int i = 0; i < dataContext.getDataCount(); i++ ) {
    InputStream is = dataContext.getStream(i);
    Properties props = dataContext.getProperties(i);

    def pivotedDataConfigsFlowTableArr = new JsonSlurper().parse(is)
    // println prettyJson(pivotedDataConfigsFlowTableArr)

    def pivotOnConfigsRowsArr = pivotedDataConfigsFlowTableArr.PivotOnConfigs.findAll{ it.Col18 }
    def rci_dynamicTableId = pivotedDataConfigsFlowTableArr.ReportContentItem_DynamicTableId

    def pivotOnConfigsResultArr = []
    pivotOnConfigsRowsArr.each { rowData ->
        def pivotedItemLabelsResultArr = []
        def labelsArr = rowData.values().toArray()[4..15].findAll()
        for (r = 0; r < labelsArr.size(); r+= 2) {
            pivotedItemLabelsResultArr << [
                Name: labelsArr[r],
                Label: labelsArr[r+1]
            ]
        }
        // println pivotedItemLabelsResultArr

        def configResultItem = [
            "PivotedDataConfigId": rowData.Col18,
            "ReportContentItem_DynamicTableId": rci_dynamicTableId,
            "PivotedItemLabels": pivotedItemLabelsResultArr,
            "Active": rowData.Col0,
            "SuppressIfNoDataForAllRows": rowData.Col1,
            "ColumnIndex": rowData.Col19,
            "SubTableIndex": rowData.Col2,
            "ColumnWidth": rowData.Col3
        ]

        pivotOnConfigsResultArr << configResultItem
    }
    // println prettyJson(pivotOnConfigsResultArr)

    is = new ByteArrayInputStream(prettyJson([
        Action: pivotedDataConfigsFlowTableArr.Action,
        Requestor: pivotedDataConfigsFlowTableArr.Requestor,
        ReportContentItem_DynamicTableId: pivotedDataConfigsFlowTableArr.ReportContentItem_DynamicTableId,
        Records: pivotOnConfigsResultArr
    ]).getBytes("UTF-8"));
    // is = new ByteArrayInputStream("".getBytes("UTF-8"));
    dataContext.storeStream(is, props);
}

private static String prettyJson(def thing) { return JsonOutput.prettyPrint(JsonOutput.toJson(thing)) }
