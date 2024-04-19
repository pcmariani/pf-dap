// val = original value before lookup
// ref = result of lookup
// outputs:
// - otherThing
// - first
// - last
// - middle

// ------------------
// for testing
def ref1 = "A"
def ref2 = "X"
def ref3 = ""
def ref4 = ""
def ref5 = ""
def ref6 = "G"

def val1 = ""
def val2 = "blah"
def val3 = "Peter"
def val4 = "C"
def val5 = "Mariani"
def val6 = ""

// ------------------
// map script starts here

def refsArr = []
def valsArr = []

refsArr[0] = ref1
refsArr[1] = ref2
refsArr[2] = ref3
refsArr[3] = ref4
refsArr[4] = ref5
refsArr[5] = ref6

valsArr[0] = val1
valsArr[1] = val2
valsArr[2] = val3
valsArr[3] = val4
valsArr[4] = val5
valsArr[5] = val6

def rawNameArr = []
refsArr.eachWithIndex { ref, index ->
    if (!ref) {
        rawNameArr << valsArr[index]
    }
    else if (ref == "X") {
        otherThing = valsArr[index]
    }
}
println rawNameArr

// removes blank elements
def nameArr = rawNameArr.findAll{it != ""}
// println nameArr

last = nameArr.pop()
first = nameArr.remove(0)
middle = nameArr.join(" ")

// println otherThing
// println last
// println first
// println middle
