package implicitconversions

object UserDatabase {
  case class User(id: Int, username: String)

  type Json = Map[String, String]
  type ApiHandler = PartialFunction[String, Json]

  def serve(h: ApiHandler): Unit = ???

  trait UserDB {
    def write(u: User): Unit
    def read (id: Int): User
  }

  abstract class DummyUserDB(dbName: String) extends UserDB {
    private var persistence: Map[Int, User] = Map()

    def write(u: User): Unit = {
      persistence = persistence.updated(u.id, u)
      println(s"Wrote to $dbName: $u")
    }
    def read (id: Int): User = persistence(id)
  }
  object UserDBSql extends DummyUserDB("SQL")

  object UserDBMongo extends DummyUserDB("MongoDB")
}
import UserDatabase._

object Implicits {
  implicit def strToInt(s: String): Int    = new Integer(s)
  implicit def intToStr(i: Int   ): String = i.toString

  implicit def userToJson(u: User): Json = Map("id" -> u.id, "username" -> u.username)
}

object E1_1_Force_Conversion extends App {
  val u = User(42, "Bob")
  UserDBSql.write(u)

  val usersApiPath = """/api/user/(\d+)""".r
  val handler: ApiHandler = {
    case usersApiPath(id) =>
      val user = UserDBSql.read(id.toInt)
      Map( "id" -> user.id.toString, "username" -> user.username )
  }
  println(handler("/api/user/42"))
}

object E1_2_Conversion extends App {
  import Implicits._

  val u = User(42, "Bob")
  UserDBSql.write(u)
  
  val usersApiPath = """/api/user/(\d+)""".r
  val handler: ApiHandler = {
    case usersApiPath(id) => UserDBSql.read(id)
  }
  println(handler("/api/user/42"))
}

object E2_Wrapper {
  class RichUser(u: User) {
    def write() = UserDBSql.write(u)
  }
  implicit def augmentUser(u: User): RichUser = new RichUser(u)
  
  class RichId(x: Int) {
    def readUser: User = UserDBSql.read(x)
  }
  implicit def augmentInt(x: Int): RichId = new RichId(x)
  implicit def augmentString(x: String): RichId = new RichId(x.toInt)

  def main(args: Array[String]): Unit = {
    import Implicits._

    val u = User(42, "Bob")
    u.write()
    
    val usersApiPath = """/api/user/(\d+)""".r
    val handler: ApiHandler = {
      case usersApiPath(id) => id.readUser
    }
    println(handler("/api/user/42"))
  }
}

object E3_1_Force_Context extends App {
  import Implicits._

  val u = User(42, "Bob")
  UserDBMongo.write(u)
  
  val usersApiPath = """/api/user/(\d+)""".r
  val handler: ApiHandler = {
    case usersApiPath(id) => UserDBSql.read(id)  // Wrong DAO used, exception happens
  }
  println(handler("/api/user/42"))  
}

object E3_Context {
  class RichUser(u: User, db: UserDB) {
    def write() = db.write(u)
  }
  implicit def augmentUser(u: User)(implicit db: UserDB): RichUser = new RichUser(u, db)
  
  class RichId(x: Int, db: UserDB) {
    def readUser: User = db.read(x)
  }
  implicit def augmentInt(x: Int)(implicit db: UserDB): RichId = new RichId(x, db)
  implicit def augmentString(x: String)(implicit db: UserDB): RichId = new RichId(x.toInt, db)

  def main(args: Array[String]): Unit = {
    import Implicits._

    implicit val db = UserDBMongo

    val u = User(42, "Bob")
    u.write()
    
    val usersApiPath = """/api/user/(\d+)""".r
    val handler: ApiHandler = {
      case usersApiPath(id) => id.readUser
    }
    println(handler("/api/user/42"))
  }
}

object E4_WrapperShorthand {
  implicit class RichUser(u: User)(implicit db: UserDB) {
    def write() = db.write(u)
  }
  
  implicit class RichId[T](x: T)(implicit db: UserDB, toInt: ToInt[T]) {
    def readUser: User = db.read(toInt(x))
  }

  trait ToInt[T] { def apply(x: T): Int }
  implicit val intToInt = new ToInt[Int   ] { def apply(x: Int   ) = x       }
  implicit val strToInt = new ToInt[String] { def apply(s: String) = s.toInt }

  def main(args: Array[String]): Unit = {
    import Implicits._

    implicit val db = UserDBMongo

    val u = User(42, "Bob")
    u.write()
    
    val usersApiPath = """/api/user/(\d+)""".r
    val handler: ApiHandler = {
      case usersApiPath(id) => id.readUser
    }
    println(handler("/api/user/42"))
  }
}