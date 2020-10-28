package example.tracepluginexample

class Foo {
    
    @TraceMethod
    fun bar(): String {
        return "bar"
    }
}

annotation class TraceMethod