import java.util.Properties;
import java.io.InputStream;
import groovy.xml.XmlUtil
import groovy.json.JsonOutput;
import com.boomi.execution.ExecutionUtil;

logger = ExecutionUtil.getBaseLogger();

for( int i = 0; i < dataContext.getDataCount(); i++ ) {
    InputStream is = dataContext.getStream(i);
    Properties props = dataContext.getProperties(i);

    def content_grid = props.getProperty("document.dynamic.userdefined.ddp_content_grid")

    def data = is.text.replaceAll(/&/,"&amp;")
    def root = new XmlSlurper().parseText("<root>$data</root>")
    // println prettyJson(root)

    // remove all @id attributes
    root.'**'.findAll { it.@id }.each { it.attributes().remove('id')}

    int tgCounterPrev = -1
    int tgCounter = 0
    def tgArr = []
    def tgMap = [:]

    // --- 1st Loop ---
    // Loop through divs and arriange data into an array of maps:
    //      tgArr[tgMap, tgMap, ...]
    // Foreach set of tablegroup, footnotes, footerText, put div data into tgMap:
    //      [
    //        html: <...>,
    //        footerText: "...",
    //        footnotes: [a: "sometext", b: "othertext" ]
    //      ]
    root.div.each{
        if (it.@class=="footnote-table-table") {
            tgMap = [:]
            // 1. add html to map
            tgMap['html'] = it.html.body.tablegroup
            tgCounter++
        }
        else {
            if (it.@class=="footnote-table-footer-row") {
                // 2. add footerText to map
                tgMap['footerText'] = it.text()
            }
            if (it.@class=="footnote-table-list") {
                tgMap['footnotes'] = [:]
                it.ul.li.each{
                    // break up footnote text into letter (footnoteArr[0]) and text (footnoteArr[1])
                    def footnoteArr = it.toString().split(/\s*:\s*/)
                    // 3. add footnotes to map
                    tgMap['footnotes'][footnoteArr[0]] = footnoteArr[1]
                }
            }
            tgCounterPrev = tgCounter
        }

        if (tgCounter != tgCounterPrev) {
            tgArr[tgCounter-1] = tgMap
        }
        // println tgCounter + " " + it.@class
    }
    // println "--------"
    // tgArr.each{it.each{println it}; println "--------"}
    // println tgArr[2].footnotes.getClass()
    // println ""

    // --- 2nd Loop ---
    // Loop through tgMap manipulating the xml
    def xmlOut = ""
    tgArr.each { tg ->

        // get id attribute on the tablegroup
        def tgid = tg.html.@reference.toString()

        // reformat <sup> nodes which contain the footnote references
        // why it's called <sup> I have no clue
        tg.html.table.tr.children().sup.each{ supNode ->
            def fnLetter = supNode.toString()
            def fnIndex = tg.footnotes.findIndexOf{it.key==fnLetter}
            def fnNewId = tgid + fnIndex
            supNode.replaceNode{ sup { a(style:"text-decoration:none;",href:"#"+fnNewId, fnLetter) } }
        }

        // create the Footnote nodes
        tg.footnotes.eachWithIndex { fnLetter, fnText, fnIndex ->
            // println ([fnLetter,fnText,fnIndex].join(" | "))
            def fnNewId = tgid + fnIndex
            def fnTextWithLetter = fnLetter + ": " + fnText
            tg.html.appendNode{
                Footnote( Seq:fnIndex, Reference:fnNewId, fnTextWithLetter)
            }
        }

        // create the FooterText nodes
        tg.html.appendNode{
            FooterText( tg.footerText )
        }

        // add tablegroup xml to xmlOut as a string
        xmlOut += XmlUtil.serialize(tg.html) - ~/^.*\?>/
    }

    // re-slurp the xml string
    def newroot = new XmlSlurper().parseText("<newroot>" + xmlOut + "</newroot>")

    // create output;; remove outter <newroot> tag
    String outData = ""
    outData = (XmlUtil.serialize(newroot)-~/^.*\?>/).replaceAll(/<\/?newroot>/, "").trim()
                // .replaceAll(/<sup>\n?\s*/, "<![CDATA[<sup>")
                // .replaceAll(/\n\s*<\/sup>\n\s*/, "</sup>]]")

    is = new ByteArrayInputStream(outData.toString().getBytes("UTF-8"));
    dataContext.storeStream(is, props);
}

private static String prettyJson(def thing) { return JsonOutput.prettyPrint(JsonOutput.toJson(thing)) }
