---
title: Implicits mechanism in Scala
code:
  location: typeclasses/implicit-conversions
  project: sbt
  file: Main
---

In our programming lives, we often encounter a need to convert one object to another. For example, an `Int` may need to be converted to `Double`. Another common need is to manage contexts. It may be required to execute the same code in different contexts - for example, a `Future` may be executed under different threading contexts which influence the multithreading behavior of the program.

To address these issues, Scala has a mechanism of implicit conversions. This mechanism is so powerful that its applications go far beyond the scope mentioned above. As far as to be talking about automatic proves of theorems about your program on compile time.

By understanding the mechanics of implicits in Scala and mastering the type classes techniques, you may be able to free yourself of countless hours of debugging the runtime.

This tutorial aims to provide an example-driven explanation of the pure functional programming techniques in Scala.

# Setting
Consider that you need to write a web application in Scala. Its job is to maintain a database of users and expose them as a JSON API. A user has an `Int` ID and a `String` name. How would you go about implementing it?

## Architecture
Probably you will start from defining a `User` case class to model the data. Next, you will want a logic to perform [CRUD](TODO) operations on the database of users. Finally, withing your web framework you will want to define an HTTP request handler to expose your JSON API to the users database.

A simple implementation may look as follows:

${codeInclude(Setting)}
```{.scala include="code/typeclasses/implicit-conversions/src/main/scala/implicitconversions/Main.scala" snippet="Setting"}
```

For simplicity, we represent JSON as a simple `Map[String, String]`. We do not define a concrete implementation for `serve` and we do not use a real database.

## Main method
After you have the above architecture, a reasonable way to define the `main` method may be as follows:

${codeInclude(E1_1_Force_Conversion)}
```{.scala include="code/typeclasses/implicit-conversions/src/main/scala/implicitconversions/Main.scala" snippet="E1_1_Force_Conversion"}
```

The above code first writes a sample user to the database. Then it defines a HTTP request handler for the JSON API to the users. In our case, a handler is just a `PartialFunction[String, Json]`. We use a regular expression to define the pattern of the path we are going to match against.

# Implicit conversions
## Introduction
There are a few ways you can improve this implementation through where the implicits mechanism may come in handy.

First, notice how we convert the `user` object to `Json` after reading it: `Map( "id" -> user.id.toString, "username" -> user.username )`. This is a pretty technical step that drains our focus when reading or writing the code. It is rather obvious too - we need to return `Json`, we have `User` and the conversion process from `User` to `Json` is well defined.

One way to simplify the above code will be to abstract away the conversion routine to a separate method. So that at the last line of the handler we write `userToJson(user)`. But in Scala, you can do even better than that simply by defining `userToJson` as an `implicit def`:

${codeInclude(userToJson)}
```{.scala include="code/typeclasses/implicit-conversions/src/main/scala/implicitconversions/Main.scala" snippet="userToJson"}
```

Once you have this `def` in scope, you can write the handler as simply:

${codeInclude(E1_2_Conversion)}
```{.scala include="code/typeclasses/implicit-conversions/src/main/scala/implicitconversions/Main.scala" snippet="E1_2_Conversion"}
```

This `implicit def` is an *implicit conversion*. It behaves almost exactly the same way an ordinary `def` does. For instance, you can call it explicitly as an ordinary `def`, it can have type parameters, multiple parameter groups etc.

In Scala, if you use an expression of some type in place where an expression of another type is expected, the compiler will not fail with a compile-time error at once. Instead, it will first try to look up an implicit conversion from the type used to the type required. The only difference between `implicit def` and a normal `def` is that the `implicit` keyword makes the `def` visible for the compiler when it looks for an implicit conversion.

## Mechanics
In the example above, the `ApiHandler` is an alias for `PartialFunction[String, Json]`. This means the compiler knows that if we define a partial function of that type (the `handler`), we must return a value of type `Json` from it. It also sees that we return `User` instead. So the expression of type `User` is used where an expression of type `Json` is expected. At this point, the compiler will start to look for an implicit conversion from `User` to `Json`.

How does it look for an implicit conversion? For example, how does the compiler know that in case of the above example it will need the `userToJson` conversion we have just defined? The answer is, it will look at the signatures of the implicit conversions in scope. It knows that `PartialFunction[String, Json]` expects a `Json` to be returned, but `User` was returned. So, the required conversion must be a function `User => Json`. The compiler will look through the `implicit` methods in scope[^1], searching for the one that takes a `User` as an argument and has `Json` as a return type. If it founds one (for example, our `userToJson`), it will implicitly wrap the `UserDBSql.read(id.toInt)` in it: `userToJson(UserDBSql.read(id.toInt))`.

So we are able to write `UserDBSql.read(id.toInt)` at the end of that handler hand have it implicitly converted to the correct time on compile time.

# Rich wrappers
Implicit conversions can be used to augment existing types with new methods. For example, instead of explicitly calling the [DAO](TODO) each time you want to perform a CRUD operation, you can augment the model objects with corresponding methods.

The way it works is by defining a wrapper class around the target type. This wrapper has a reference to the object it wraps and contains additional methods. Then you define an implicit conversion from the target type to the wrapper.

For example, you can augment the `User` with a `write()` operation and the `String` with `readUser()` operation as follows (notice how the `read` on an `Int` is ambiguous: in general, we have more model objects than just `User`, so we'd better specify it clearly in the signature which one we are trying to read):

${codeInclude(E2_Wrapper_conversions)}
```{.scala include="code/typeclasses/implicit-conversions/src/main/scala/implicitconversions/Main.scala" snippet="E2_Wrapper_conversions"}
```

After you have defined the above code, you can rewrite our example as follows:

${codeInclude(E2_Wrapper_example)}
```{.scala include="code/typeclasses/implicit-conversions/src/main/scala/implicitconversions/Main.scala" snippet="E2_Wrapper_example"}
```

The compiler will encounter the `u.write()` and `id.toInt.readUser()`, but will not find the `write()` and `readUser()` methods in `User` and `Int` correspondingly. It will then look at the type of the object the method is called on and will try to find a conversion from that type to a type that happens to have the required method.

This is a common pattern in Scala: whenever you want to "inject" a method into a class, you define a rich wrapper and a conversion from that class to the wrapper as above. Of course, the term "inject" here is metaphorical: in reality, the bytecode is not modified.

# Implicit arguments
A `def` may have its last argument group defined as `implicit`. This means that you are not required to pass these arguments explicitly (in fact, you can ignore the entire argument group) and the compiler will resolve them from the implicit scope.

It works similarly to how the compiler looks for implicit conversions: just that in case of the conversions it looks for methods with a particular signature (`TargetType => RequiredType`) and in case of implicit arguments it looks for `val`s or `def`s (yes, `val`s can also be declared `implicit`, for just this purpose) with the required return type.

In our example, this can be useful for managing database contexts. Consider, for instance, that we want a support for the MongoDB backend:

${codeInclude(MongoDB)}
```{.scala include="code/typeclasses/implicit-conversions/src/main/scala/implicitconversions/Main.scala" snippet="MongoDB"}
```

Then, whenever we want to change the backend we use, we will need to change every occurrence of it in the code. That's not very [DRY](TODO), so normally you would assign the backend you want to use to a variable and reference it every time you need the backend:

${codeInclude(E3_1_Force_Context_conversions)}
```{.scala include="code/typeclasses/implicit-conversions/src/main/scala/implicitconversions/Main.scala" snippet="E3_1_Force_Context_conversions"}
```

This introduces a dependency on a global variable, however. `RichUser`, `RichId` and, in fact, any code that needs a backend must know exactly where this variable is located. This can greatly reduce flexibility in case of large code bases: every time you work with the backend, you are forced to think globally, and this reduces focus on the current task. Modular, purely local solutions where you need to think only about the local piece of code you are currently writing, are preferred.

For such situations when there is a code dependent on some context, implicit arguments are a good use case:

${codeInclude(E3_Context_conversions)}
```{.scala include="code/typeclasses/implicit-conversions/src/main/scala/implicitconversions/Main.scala" snippet="E3_Context_conversions"}
```

Notice how the `write()` and `readUser()` methods no longer depend on anything outside the scope of their parent classes. Next, notice how the backend is passed as an implicit argument to the implicit conversion method. Once the compiler needs, looks for and discovers the implicit conversion with an implicit argument list, it will look up these implicit arguments and inject them into the original `implicit def`:

${codeInclude(E3_Context_example)}
```{.scala include="code/typeclasses/implicit-conversions/src/main/scala/implicitconversions/Main.scala" snippet="E3_Context_example"}
```

We still have the backend stored in one variable, but this time no code that requires it references it directly. Instead, the implicits mechanism mediates between the variable and the code that depends on it.

In this sense, you can think of the implicits mechanism as a way to abstract away the global scope. Whenever you need to reference something from the global scope, you don't directly point the location of that something. Instead, you merely declare the need for this something to be provided - and the compiler looks it up in the implicit scope. Of course, you need to make sure everything you need is on the implicit scope at the point where you want to use it.

# `implicit class` pattern
The rich wrapper pattern discussed above is so commonly used that there's a language-level support of it in Scala. You can declare a class that has exactly one constructor parameter `implicit` - this will declare a class as usual, but also an implicit conversion from its single constructor argument to that class:

${codeInclude(E4_WrapperShorthand_conversions)}
```{.scala include="code/typeclasses/implicit-conversions/src/main/scala/implicitconversions/Main.scala" snippet="E4_WrapperShorthand_conversions"}
```

Notice how classes can also have an implicit argument group.

# A word of caution
There is one other rather technical conversion in our code that potentially can be simplified away with implicit conversions - it is `id.toInt` in `UserDBSql.read(id.toInt)`.

`case usersApiPath(id)` will match the `String` fed into the handler against the regex we defined and will capture the group with the requested user id. The problem is, our ids are `Int`s, but the regex captures everything as a `String`. So in order to call the `read` method on the database access object, we first need to convert a `String` to an `Int` - hence `id.toInt`.

At a glance, this is a case very similar to the previous one: a `String` used where `Int` is expected. The only difference is that in the previous case the expression in question was returned from a function, and in this case it is fed to the function as an argument. But this does not make any difference - the implicits mechanism will work all the same here.

Indeed, if we define an implicit conversion from `String` to `Int`, we may be able to write `UserDBSql.read(id)`, even though `id` is a `String` and `read` wants an `Int`. Here is an example of a conversion that will do the job:

```scala
implicit def strToInt(s: String): Int = Integer.parseInt(s)
```

So what is the problem with this? Further, we face a need to convert strings to ints very frequently - so why did Scala not made such a conversion available out of the box, in scope by default?

First consider the scenario without an implicit conversion. Consider what happens if someone glances at `UserDBSql.read()` and thinks it queries the database by username, not an id. Not an unlikely scenario: in a reasonable implementation of the database schema will, every user can be uniquely identified by both its id and the username. So without looking at the `read()`'s signature, one may assume it accepts a `String` which is a username.

Now, this person may proceed to calling `UserDBSql.read("Bob")`. A great strength of the languages with a strong static type system is that they catch many errors and bugs on compile time. In our case, the compiler will see that you have made a mistake simply by looking at the types, and will emit an error. The programmer will see that an `Int` was expected but `String` was found, and will understand that they need to pass an id there instead of a username.

What happens when we have an implicit conversion from `String` to `Int` in scope? In this case, the compiler will not fail with an error, since it will know how to convert `"Bob"` to `Int`. The problem is, it *knows* how to do that on compile time, but actually *does* in on runtime. So the error will remain undiscovered on compile time, and will result in an obscure exception on the runtime, which is much harder to discover and debug.

Pure functional programming and strong type system aims to provide you with additional safeguard against errors and bugs, catching as much of them as possible on compile time. Implicits mechanism is a powerful feature, however with great power comes great responsibility. If you overuse them, you may harm the robustness of your program and make it harder to understand (known as "implicits hell").

So when do you use them and when - not? When you bring implicit conversions in scope you instruct the compiler to make certain assumptions about your program. It assumes you want it to perform the conversion whenever applicable without asking you. In each individual case, you should consider the consequences of this additional freedom you give the compiler.
