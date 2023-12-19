import java.util.Properties;
import java.io.InputStream;
import groovy.xml.XmlUtil
import com.boomi.execution.ExecutionUtil;
 
def NEWLINE = System.lineSeparator()
def OFS = "|^|"     // Output Field Separator

for( int i = 0; i < dataContext.getDataCount(); i++ ) {
    InputStream is = dataContext.getStream(i);
    Properties props = dataContext.getProperties(i);

    def resultSet = new XmlSlurper().parseText(is.text)
    def orientation = resultSet.RecordType
    def outArr = []

    // Source data can be pretty dirty.
    // - It can have rows with no data, i.e. rows that have TRs and TDs with Keys but no Vals.
    // - All rows have a fixed number of cells, but only some will be populated. So a TR will
    //   have some TDs with Vals, then many with no Vals.
    //   - We can't just remove all TDs with no Vals, becuase it's possible that some TDs
    //     within the range of real data will have no Val.
    // Solution:
    // - for each row, traverse backwards removing TDs with no Vals, until hitting
    //   at TD with a Val, then stop traversing.
    resultSet.TR.collect{it.TD.Val}.each{ row ->
        // Skip a row if all vals are blank
        // if (row != "") {
        //     // put row vals into array
            def rowData = row.collect()
        //     def removeBlanks = true
        //     // traverse backwards through vals
        //     for (int j = rowData.size()-1; j >= 0; j--) {
        //         if (removeBlanks && rowData[j] == "") rowData.remove(j)
        //         else removeBlanks = false
        //     }
            outArr << rowData
        // }
    }
    // println outArr

    if (outArr) {
        // It's possible that the last TD within the range of real data has a blank val,
        // we can't trust that each row has the same number of vals.
        // Solution:
        // - find max with of all rows
        // - if any rows are less than max, add blank cell until the widths are all the same
        def dataMaxWidth = outArr.collect{it.size()}.max()
        // outArr.each{
        //     while (it.size() < dataMaxWidth) it << ""
        // }
        // println dataMaxWidth

        // Headers for vertical tables
        // - sourced from the Key fields of the TDs of the the first TR,
        //   i.e. all the Keys of the first row.
        // - will be displayed in resulting table
        // Headers for horizontal tables
        // - sourced from the Key of the first TD of each TR,
        //   i.e. the first Key of all the rows
        // - each header is prepended to it's corresponding row
        // - the downstream processor doesn't consider these to be headers, so we create
        //   dummy headers that will be ignored
        def headers = []
        if (orientation == "Vertical") {
            // source headers
            headers = resultSet.TR[0].TD.Key
            // prepend to outArr, so they are the first row
            // outArr.add(0,headers.collect{ it.join(OFS) })
            props.setProperty("document.dynamic.userdefined.ddp_displayHeaders", "true")
        }
        else {
            // source "headers" into first column
            def firstColumn = resultSet.TR.findAll{it.TD.Val != ""}.collect{it.TD[0].Key}
            // need to transpose data to prepend firstColumn as first row of arr
            outArr = [firstColumn] + outArr.transpose()
            // transpose again to orient data horizontally
            outArr = outArr.transpose()
            // create dummy headers
            if (dataMaxWidth) (0..dataMaxWidth).each { d -> headers << ["header" + d]}
            props.setProperty("document.dynamic.userdefined.ddp_displayHeaders", "false")
        }

        // output as |^| delimited tabular data for downstream formatting
        def outData = outArr.collect{ it.join(OFS) }.join(NEWLINE)

        props.setProperty("document.dynamic.userdefined.ddp_sqlColumnNames", headers.flatten().join(OFS))
        is = new ByteArrayInputStream(outData.toString().getBytes("UTF-8"));
    }

    else {
        props.setProperty("document.dynamic.userdefined.ddp_sqlColumnNames", "Warning")
        is = new ByteArrayInputStream("No data returned".toString().getBytes("UTF-8"));
    }

    props.setProperty("document.dynamic.userdefined.ddp_isPivot", "false")

    dataContext.storeStream(is, props);
}
