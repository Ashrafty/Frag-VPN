class ServerModel {
  final String id;
  final String name;
  final String country;
  final String city;
  final int pingTime; // in milliseconds
  final int serversAvailable;
  final bool isPremium;
  final bool isSelected;
  final String? outlineKey; // Outline VPN configuration key

  ServerModel({
    required this.id,
    required this.name,
    required this.country,
    required this.city,
    required this.pingTime,
    required this.serversAvailable,
    this.isPremium = false,
    this.isSelected = false,
    this.outlineKey,
  });

  ServerModel copyWith({
    String? id,
    String? name,
    String? country,
    String? city,
    int? pingTime,
    int? serversAvailable,
    bool? isPremium,
    bool? isSelected,
    String? outlineKey,
  }) {
    return ServerModel(
      id: id ?? this.id,
      name: name ?? this.name,
      country: country ?? this.country,
      city: city ?? this.city,
      pingTime: pingTime ?? this.pingTime,
      serversAvailable: serversAvailable ?? this.serversAvailable,
      isPremium: isPremium ?? this.isPremium,
      isSelected: isSelected ?? this.isSelected,
      outlineKey: outlineKey ?? this.outlineKey,
    );
  }

  @override
  String toString() {
    return 'ServerModel(id: $id, name: $name, country: $country, city: $city, pingTime: $pingTime, serversAvailable: $serversAvailable, isPremium: $isPremium, isSelected: $isSelected, outlineKey: $outlineKey)';
  }

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) return true;

    return other is ServerModel &&
      other.id == id &&
      other.name == name &&
      other.country == country &&
      other.city == city &&
      other.pingTime == pingTime &&
      other.serversAvailable == serversAvailable &&
      other.isPremium == isPremium &&
      other.isSelected == isSelected &&
      other.outlineKey == outlineKey;
  }

  @override
  int get hashCode {
    return id.hashCode ^
      name.hashCode ^
      country.hashCode ^
      city.hashCode ^
      pingTime.hashCode ^
      serversAvailable.hashCode ^
      isPremium.hashCode ^
      isSelected.hashCode ^
      (outlineKey?.hashCode ?? 0);
  }
}
