# Panduan Penggunaan

Dokumen ini menjelaskan cara memakai Module Composer dari konfigurasi Gradle,
command CLI, sampai penggunaan `distributions.yml`.

## Konsep Singkat

Module Composer memilih module berdasarkan input CLI atau preset YAML.

```text
1 module dipilih
-> menjalankan atau build module itu langsung

Lebih dari 1 module dipilih
-> generate host sementara
-> import configuration class tiap module
-> run/build satu aplikasi gabungan
```

`distributions.yml` bersifat optional. Gunakan `-Pmodules` untuk kombinasi
sementara, dan gunakan `-Pdistribution` untuk kombinasi yang sering dipakai.

## Konfigurasi Root Project

Plugin root dipasang hanya di root Gradle project aplikasi.

```kotlin
plugins {
    id("io.github.bud127.module-composer")
}
```

Konfigurasi minimal:

```kotlin
moduleComposer {
    distributionFile.set("distributions.yml")
}
```

Konfigurasi lengkap yang umum dipakai:

```kotlin
moduleComposer {
    distributionFile.set("distributions.yml")

    springBootVersion.set("3.5.7")
    dependencyManagementVersion.set("1.1.7")
    javaVersion.set(21)

    commonProjectPaths.set(
        listOf(":platform-health")
    )

    commonConfigurationClasses.set(
        listOf(
            "com.example.platform.health.HealthConfiguration"
        )
    )
}
```

Default:

```text
framework              = spring-boot
distributionFile       = distributions.yml
generatedHostDirectory = build/module-composer/generated/combined-app
outputJar              = build/module-composer/output/combined-app.jar
springBootVersion      = 3.5.7
dependencyManagement   = 1.1.7
javaVersion            = 21
```

Jika `applicationName` diberikan dari CLI atau YAML dan lokasi default masih
dipakai, output menjadi:

```text
build/module-composer/generated/<applicationName>/
build/module-composer/output/<applicationName>.jar
```

## Konfigurasi Module

Setiap module yang bisa dipilih harus memakai plugin module composer.

```kotlin
plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("io.github.bud127.module-composer-module")
}

moduleComposerModule {
    name.set("payment")
    configurationClass.set(
        "com.example.payment.PaymentModuleConfiguration"
    )
    standaloneRunTask.set("bootRun")
    standaloneBuildTask.set("bootJar")
}
```

`name` adalah nama logical module yang dipakai di CLI dan YAML. Nama default
diambil dari nama project Gradle dengan prefix `module-` dihapus.

`configurationClass` adalah class yang akan di-import oleh generated host saat
lebih dari satu module digabungkan.

## Command Discovery

Lihat module yang tersedia:

```bash
./gradlew listModules
```

Lihat distribution preset yang tersedia:

```bash
./gradlew listDistributions
```

Lihat rencana eksekusi tanpa menjalankan build:

```bash
./gradlew explain -Pmodules=payment,notification
./gradlew explain -Pdistribution=enterprise
```

## CLI Tanpa YAML

Run satu module langsung:

```bash
./gradlew bundleRun -Pmodules=payment
```

Run satu module dengan port:

```bash
./gradlew bundleRun -Pmodules=payment -Pport=9090
```

Run beberapa module sebagai aplikasi gabungan:

```bash
./gradlew bundleRun -Pmodules=payment,notification
```

Build beberapa module sebagai bundle JAR:

```bash
./gradlew bundleBuild -Pmodules=payment,notification
```

Build dengan nama aplikasi dan nama bundle custom:

```bash
./gradlew bundleBuild \
  -Pmodules=payment,notification \
  -PapplicationName=custom-service
```

Output:

```text
build/module-composer/generated/custom-service/
build/module-composer/output/custom-service.jar
```

## Distribution YAML

Buat file `distributions.yml` di root project aplikasi.

```yaml
version: 1

distributions:
  payment-only:
    modules:
      - payment

  community:
    applicationName: community-service
    modules:
      - payment
      - notification

  enterprise:
    applicationName: enterprise-service
    modules:
      - payment
      - notification
      - audit
```

`applicationName` optional. Jika diisi, nilainya dipakai untuk:

- `spring.application.name` di generated Spring Boot application
- generated host directory default
- final bundle name default

Command memakai distribution:

```bash
./gradlew bundleRun -Pdistribution=community
./gradlew bundleBuild -Pdistribution=enterprise
```

Output untuk contoh `enterprise`:

```text
build/module-composer/generated/enterprise-service/
build/module-composer/output/enterprise-service.jar
```

## Override Distribution

Tambah module di luar preset:

```bash
./gradlew bundleBuild \
  -Pdistribution=community \
  -PincludeModules=audit
```

Exclude module dari preset:

```bash
./gradlew bundleBuild \
  -Pdistribution=enterprise \
  -PexcludeModules=audit
```

Override nama aplikasi dari CLI:

```bash
./gradlew bundleBuild \
  -Pdistribution=enterprise \
  -PapplicationName=custom-enterprise
```

Prioritas nama aplikasi:

```text
-PapplicationName
-> distributions.yml applicationName
-> combined-app
```

## Parallel Build

Untuk mempercepat build artifacts module dalam satu kombinasi, gunakan parallel
build Gradle:

```bash
./gradlew bundleBuild -Pdistribution=enterprise --parallel
```

Generated host directory sudah unik per `applicationName`, sehingga hasil akhir
antar distribution dengan nama berbeda tidak saling menimpa:

```text
build/module-composer/generated/community-service/
build/module-composer/generated/enterprise-service/
build/module-composer/output/community-service.jar
build/module-composer/output/enterprise-service.jar
```

## Catatan Output

Jika `moduleComposer.outputJar` dikonfigurasi ke file custom, nama file custom
itu dihormati dan tidak diganti oleh `applicationName`.

Jika `applicationName` sama untuk dua command berbeda, generated host dan final
JAR dengan nama itu akan dipakai ulang dan bisa tertimpa oleh build berikutnya.

## Error Umum

`Task 'bundleBuild' not found` biasanya berarti command dijalankan dari Gradle
root yang tidak memakai plugin `io.github.bud127.module-composer`.

`Use either -Pmodules or -Pdistribution, not both` berarti pemilihan module
ambigu. Pilih salah satu.

`Unknown module` berarti nama module belum terdaftar melalui
`io.github.bud127.module-composer-module`.

`Unknown distribution` berarti nama distribution tidak ada di
`distributions.yml`.

`Invalid application name` berarti nama aplikasi memakai karakter yang tidak
didukung. Gunakan huruf, angka, titik, underscore, atau dash, dan mulai dengan
huruf atau angka.
