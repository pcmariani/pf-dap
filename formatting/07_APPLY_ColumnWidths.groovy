import java.util.Properties;
import java.io.InputStream;
import groovy.xml.XmlUtil
import groovy.json.JsonSlurper
import groovy.json.JsonOutput;
import com.boomi.execution.ExecutionUtil;
 
// def NEWLINE = System.lineSeparator()
// def IFS = /\|\^\|/  // Input Field Separator
// def OFS = "|^|"     // Output Field Separator

for( int i = 0; i < dataContext.getDataCount(); i++ ) {
    InputStream is = dataContext.getStream(i);
    Properties props = dataContext.getProperties(i);

    def respectColumnWidths = (props.getProperty("document.dynamic.userdefined.ddp_respectColumnWidths") ?: "true").toBoolean()

    def sourcesJson = props.getProperty("document.dynamic.userdefined.ddp_Sources")
    def categoryRowconfig = new JsonSlurper().parseText(sourcesJson).Records[0]?.PivotGroupByCategoryRows
    int numCategoryRows = categoryRowconfig?.NumGroupByColsToConvert ?: 0
    // println numCategoryRows

    // HACK FOR TESTING - remove once UI and API are complete {{{
    // Boolean isPHRDReport = (props.getProperty("document.dynamic.userdefined.ddp_IsPHRDReport") ?: "false").toBoolean()
    // if (isPHRDReport) numCategoryRows = 1
    // println isPHRDReport
    // }}} END HACK

    // if (isPivot) {
    def tableGroup = new XmlSlurper().parseText(is.text.replaceFirst(/(?i)(<\/tablegroup>).*$/,"\$1"))
    // println tableGroup

    tableGroup.table.each { table ->
        // extract column widths from table cells
        def widthsArr = table.tr[0].children().findAll{it.@columnWidth != ""}.collect{
            if (it.@columnWidth == "null") 10
            else it.@columnWidth.toInteger()
        }

        // removes an item at the from of the array for each categoryRow (to data there is only one possible)
        // the category rows are created in a later script
        if (numCategoryRows > 0) {
            (0..numCategoryRows-1).each{ widthsArr.remove(0) }
        }
        // println widthsArr

        // calculate the width percentages
        def widthsPercentsArr = toPercents(widthsArr.collect{it as int})
        // println widthsPercentsArr

        // for each category row item that was removed, add back a 0
        if (numCategoryRows > 0) {
            (0..numCategoryRows-1).each{ widthsPercentsArr.add(0, 0) }
        }
        // println widthsPercentsArr

        // replace the columnWidth attribute with a style attribute containing the width percentage
        table.tr.each { tr ->
            tr.children().findAll{it.@columnWidth != ""}.eachWithIndex{ cell, c ->
                // println c + "  |  " + cell
                cell.attributes().remove('columnWidth')
                if (respectColumnWidths) cell.@style = "width:" + widthsPercentsArr[c].toString() + "%"
            }
        }
    }

    def outData = XmlUtil.serialize(tableGroup)
    // def outData = "-"
    is = new ByteArrayInputStream(outData.toString().getBytes("UTF-8"));
    // }
    dataContext.storeStream(is, props);
}

def toPercents(rawWidthsArr) {
    // calculate percentage multiplier
    def multiplier = 100 / rawWidthsArr.sum()
    // create array with widths as percentages
    // can add up to more or less than 100 because of rounding
    def widthsArr = rawWidthsArr.collect { (it.multiply(multiplier) as double).round() }
    // logger.warning("widthsArr: " + widthsArr.toString())
    // calculate the difference between 100 and the sum of widths
    def difference = 100 - widthsArr.sum()
    // logger.warning("difference: " + difference)
    /* Distribute the difference among the highest/lowest widths */
    // create a sorted version of the widthsArr
    def widthsArrSorted = widthsArr.clone().sort()
    if (difference < 0) widthsArrSorted.reverse()
    // loops as many times as the difference between 100 and the sum of widths
    for (j=0;j<Math.abs(difference);j++) {
        // find the value of sorted array in the original arrray
        def width = widthsArr.indexOf(widthsArrSorted[j])
        // add one or subtract one from it
        if (difference > 0) widthsArr[width] = widthsArr[width] + 1
        else  widthsArr[width] = widthsArr[width] - 1
    }
    return widthsArr
}
