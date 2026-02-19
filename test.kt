import java.io.File
fun main() {
    val path = "C:\Users\test.json"
    val escaped = path.replace("\", "\\")
    println("Original: $path")
    println("Escaped: $escaped")
    val jsonContent = "{\"credentialPath\": \"$escaped\"}"
    println("JSON Content: $jsonContent")
    // Simulate reading via substring
    val firstQuote = jsonContent.indexOf("C:")
    val secondQuote = jsonContent.lastIndexOf("\"")
    val readPath = jsonContent.substring(firstQuote, secondQuote)
    println("Read Path (raw): $readPath")
    println("Read Path == Original ? ${readPath == path}")
}
