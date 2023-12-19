/**
 * Takes an HTML table and, within a specified range, merge cells vertically (doesn't support
 * horrisontally yet) if the values are the same. For Batch Analysis, that only applies to 
 * the first two columns of the data rows. Therefore the range is 3,-1,0,0. This means that
 * cell need only be merged if they are in rows 4 (rowStart=3) to the end (rowEnd=-1), and 
 * in the first column (colStart=0, colEnd=0).
 * 
 * Note: This script must be folloed by APPLY_Colspons to work becuase the TH/TDs marked "DELETE"
 * are deleted in that script.
 * 
 */
 
import java.util.Properties;
import java.io.InputStream;
import groovy.json.JsonSlurper
import com.boomi.execution.ExecutionUtil;

logger = ExecutionUtil.getBaseLogger()

for( int i = 0; i < dataContext.getDataCount(); i++ ) {
    InputStream is = dataContext.getStream(i);
    Properties props = dataContext.getProperties(i);

    def root = new XmlSlurper().parse(is)
    def rowspanRange = props.getProperty("document.dynamic.userdefined.ddp_rowspanRange")
    
    root.'**'.findAll() { node -> node.name() == "table" }.eachWithIndex() { table, e ->
        def (rowStart, rowEnd, colStart, colEnd) = rowspanRange.split(/\s*,\s*/).collect{it as int}
        
        rowEnd = getEndIndex(table.tr.size(), rowEnd)
        colEnd = getEndIndex(table.tr[0].children().size(), colEnd)
        
        def rowSpanArr = getRowSpanAndDeletionArr(table, rowStart, rowEnd, colStart, colEnd)
        
        table.tr[rowStart..rowEnd].eachWithIndex() { tr, r ->
            tr.children()[colStart..colEnd].eachWithIndex() { it, s ->
                if (rowSpanArr[r][s] == "DELETE") {
                    it.@delete = "TRUE"
                }
                else if (rowSpanArr[r][s] != null) {
                    it.@rowSpan = rowSpanArr[r][s].toString()
                }
            }
        }
    }

    def outData = groovy.xml.XmlUtil.serialize(root).replaceAll("\\<\\?xml(.+?)\\?\\>", "").trim()
    
    is = new ByteArrayInputStream(outData.toString().getBytes("UTF-8"));
    dataContext.storeStream(is, props);
}

/**
 * traverse backwards through the columns (the TH's and TD's which are the
 * children of the TR). If the item above the current one is the same, start
 * a counter to count how many of the same items there are. If the next item
 * above is different then the counter resets.
 * output:   Array  2-dimentional array where the rows and cols correspond to the
 *                  rows and cols of the input html table, but contain metadata:
 *                  either the @rowspan value of that TD or TH, or DELETE which
 *                  indcates that that TD or TH should be deleted (because it's
 *                  spanned)
 */
def getRowSpanAndDeletionArr(table, rowStart, rowEnd, colStart, colEnd) {
    def tdsPrevsArr = []
    def countArr = []
    def delArr = []
    table.tr[rowStart..rowEnd].reverse().eachWithIndex() { tr, r ->
      def numCols = colEnd - colStart + 1
      countArr[r] = [0]*numCols
      delArr[r] = [null]*numCols
      tr.children()[colStart..colEnd].eachWithIndex() { it, s ->
        // if the value of the previous element is the same,
        if (it == tdsPrevsArr[s]) {
          // set the counter for this element to the preveious + 1
          countArr[r][s] = countArr[r-1][s] + 1
          // set the action for the previous element to delete
          delArr[r-1][s] = "DELETE"
        }
        tdsPrevsArr[s] = it
      }
    }
    def combinedArr = []
    delArr.eachWithIndex{ row, r ->
        combinedArr[r] = []
        row.eachWithIndex { col, c ->
            combinedArr[r][c] = col == "DELETE" ? col : ( countArr[r][c] > 0 ? countArr[r][c] + 1 : null)
        }
    }
    return combinedArr.reverse()
}

def getEndIndex(numItems, endIndex) {
  if (endIndex == -1) return numItems - 1
  else return endIndex
}
