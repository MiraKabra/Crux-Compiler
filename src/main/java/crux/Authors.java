package crux;

final class Authors {
  // TODO: Add author information.
  static final Author[] all = {new Author("Mira Kabra", "69963587", "mkabra1"),};
}


final class Author {
  final String name;
  final String studentId;
  final String uciNetId;

  Author(String name, String studentId, String uciNetId) {
    this.name = name;
    this.studentId = studentId;
    this.uciNetId = uciNetId;
  }
}
