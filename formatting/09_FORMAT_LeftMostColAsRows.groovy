/* In pivoted tables, the left column or columns are the GroupBy columns. They are
 * like the category label for their row. Sometimes the user wants to put that category
 * label above their rows instead of on the side. This script allows that for the first
 * GroupBy column on the left. 
 *
 * Example:
 *
 *     this table
 *
 *         +----------+--------------+----+----+----+----+
 *         | Category | Sub-category | t1 | t2 | t3 | t4 |
 *         +----------+--------------+----+----+----+----+
 *         | First    | a            | x  | x  | x  | x  |
 *         +----------+--------------+----+----+----+----+
 *         | First    | b            | x  | x  | x  | x  |
 *         +----------+--------------+----+----+----+----+
 *         | Second   | a            | x  | x  | x  | x  |
 *         +----------+--------------+----+----+----+----+
 *         | Second   | b            | x  | x  | x  | x  |
 *         +----------+--------------+----+----+----+----+
 *         | Second   | c            | x  | x  | x  | x  |
 *         +----------+--------------+----+----+----+----+
 *
 *     becomes
 *
 *           +--------------+----+----+----+----+
 *           | Sub-category | t1 | t2 | t3 | t4 |
 *           +--------------+----+----+----+----+
 *           |               First              |
 *           +--------------+----+----+----+----+
 *           | a            | x  | x  | x  | x  |
 *           +--------------+----+----+----+----+
 *           | b            | x  | x  | x  | x  |
 *           +--------------+----+----+----+----+
 *           |              Second              |
 *           +--------------+----+----+----+----+
 *           | a            | x  | x  | x  | x  |
 *           +--------------+----+----+----+----+
 *           | b            | x  | x  | x  | x  |
 *           +--------------+----+----+----+----+
 *           | c            | x  | x  | x  | x  |
 *           +--------------+----+----+----+----+
 *
*/

import java.util.Properties;
import java.io.InputStream;
import groovy.json.JsonSlurper

for( int i = 0; i < dataContext.getDataCount(); i++ ) {
  InputStream is = dataContext.getStream(i);
  Properties props = dataContext.getProperties(i);

  /* INPUTS */
  def categoryRowPrependText = props.getProperty("document.dynamic.userdefined.ddp_categoryRowPrependText") ?: ""

  /* LOGIC */
  def root = new XmlParser().parse(is)
  root.'**'.findAll() { node -> node.name() == "table" }.eachWithIndex() { table, t ->

    // when there is a rowspan, save it and then decriment it for each row that is spanned
    int rowSpanValSaved = 0
    // for each category row that is added add one to this var
    int numRowsAdded = 0

    // loop though the TRs
    table.tr.eachWithIndex { tr, f ->
      // save the node
      def firstCellInRow = tr.children()[0]
      int firstCellInRow_rowSpan = firstCellInRow.@rowSpan ? firstCellInRow.@rowSpan as int : 0

      // save the rowspan or decriment the saved one
      Boolean firstCellInRow_hasRowSpan = false
      if (firstCellInRow_rowSpan != 0) {
        firstCellInRow_hasRowSpan = true
        rowSpanValSaved = firstCellInRow_rowSpan
      } else if (rowSpanValSaved) {
        rowSpanValSaved--
      }

      // if the row is not a header row (it's children are td's and not th's)
      // create the category row
      if (firstCellInRow.name() == "td") {
        int numCols = firstCellInRow.parent().children().size()
        // new tr
        def newtr = new Node(table, 'tr')
        // new td - adds colSpan attribute and copies all attributes from firstCellInRow
        def newtd = new Node(newtr, 'td', [colSpan:numCols] + firstCellInRow.attributes(), categoryRowPrependText + (firstCellInRow.value()[0] as String))
        // the best we can do above is append the new row to the bottom of the table
        // we need to basically cut (remove) it and paste (add) it at the correct row index
        // we need the numRowsAdded so that we don't keep pasting the new rows at the same index
        table.remove(newtr)
        table.children().add(f+numRowsAdded, newtr)
        numRowsAdded++
      }

      // delete all the cells in the first column (if they haven't been spanned)
      if (firstCellInRow_hasRowSpan || !rowSpanValSaved) {
        firstCellInRow.replaceNode{}
      }
      // println "@rowSpan: " + firstCellInRow_rowSpan + " ::: rowSpanValSaved: " + rowSpanValSaved
    }
  }

  /* OUTPUT */
  String outData
  outData = groovy.xml.XmlUtil.serialize(root).replaceFirst("\\<\\?xml(.+?)\\?\\>", "").trim() //.replaceAll(/<tr\s*?\/\s*>/,"")
  is = new ByteArrayInputStream(outData.toString().getBytes("UTF-8"));
  dataContext.storeStream(is, props);
}

