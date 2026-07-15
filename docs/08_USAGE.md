# Panduan Penggunaan

Dokumen ini menjelaskan cara memakai Module Composer dari konfigurasi Gradle,
command CLI, sampai penggunaan distribution YAML.

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

Distribution YAML bersifat optional. Gunakan `-Pmodules` untuk kombinasi
sementara, dan gunakan `-Pdistribution` untuk kombinasi yang sering dipakai.
Distribution bisa disimpan di `distributions.yml` atau satu file per distribution
di `distributions/<name>.yaml`. Keduanya tidak boleh dipakai dalam command yang
sama.

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
    distributionFile.set("distributions")
}
```

Konfigurasi lengkap yang umum dipakai:

```kotlin
moduleComposer {
    distributionFile.set("distributions")

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
springBootVersion      = plugin managed default
dependencyManagement   = plugin managed default
javaVersion            = 21
```

Jika perlu override versi Spring Boot atau dependency-management plugin, ambil
nilainya dari source terpusat seperti version catalog atau Gradle property,
bukan literal di banyak build script.

Jika `applicationName` diberikan dari CLI atau YAML dan lokasi default masih
dipakai, output menjadi:

```text
build/module-composer/generated/<applicationName>/
build/module-composer/output/<applicationName>.jar
```

Output di atas hanya berlaku untuk generated host mode. Untuk satu module,
`bundleBuild` menjalankan standalone build task milik module tersebut dan output
mengikuti konfigurasi module.

`applicationName` harus memakai huruf, angka, titik, underscore, atau dash, dan
harus dimulai dengan huruf atau angka.

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

Task publik dari root plugin:

```text
listModules
listDistributions
explain
bundleRun
bundleBuild
```

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

Task internal/generated seperti `prepareGeneratedHost`, `runGeneratedHost`,
`buildGeneratedHost`, dan `copyGeneratedHostJar` dibuat saat diperlukan untuk
multi-module mode. Biasanya task ini tidak dipanggil langsung.

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

Build dengan validasi test selected modules:

```bash
./gradlew bundleBuild \
  -Pmodules=payment,notification \
  -Pvalidation=test
```

Nilai validasi yang didukung:

```text
none
test
check
```

Default-nya `none`. `test` menjalankan `:module-x:test` untuk setiap selected
module. `check` menjalankan `:module-x:check`.

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

Ada dua format distribution yang didukung.

Format lama untuk banyak preset dalam satu file `distributions.yml`:

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

Format baru untuk satu distribution per file:

```text
distributions/document-platform.yaml
```

```yaml
name: document-platform
version: 0.1.0

modules:
  - document
  - email
  - upload

artifact:
  fileName: application.jar

container:
  image: ghcr.io/bud127/document-platform
  baseImage: eclipse-temurin:21-jre
  hostPort: 8080
  containerPort: 8080
```

Command:

```bash
./gradlew bundleBuild -Pdistribution=document-platform
```

Output multi-module dengan `artifact.fileName`:

```text
build/module-composer/generated/document-platform/
build/module-composer/output/application.jar
```

`applicationName` optional. Jika diisi, nilainya dipakai untuk:

- `spring.application.name` di generated Spring Boot application
- generated host directory default
- final bundle name default

Untuk single distribution file, `name` dipakai sebagai default
`applicationName` jika `applicationName` tidak diisi.

`artifact.fileName` optional. Jika diisi dan nama file
`moduleComposer.outputJar` masih memakai default `combined-app.jar`, nama final
JAR mengikuti nilai itu.

`container.image`, `container.baseImage`, `container.hostPort`, dan
`container.containerPort` optional. `hostPort` adalah port host yang dipublish
oleh docker compose. `containerPort` adalah port container yang dipakai docker
compose dan Dockerfile `EXPOSE`. Metadata container ditampilkan oleh `explain`
dan `listDistributions`. Untuk multi-module `bundleBuild`, metadata container
juga menghasilkan file container di folder output yang unik per aplikasi. Nama
service container diturunkan dari `applicationName` dan dinormalisasi untuk
Docker compose service dan nama folder:

```text
build/module-composer/output/containers/<containerServiceName>/Dockerfile
build/module-composer/output/containers/<containerServiceName>/docker-compose.yml
```

Compose file yang dihasilkan akan build image dari JAR final dan map
`container.hostPort:container.containerPort`.

Dockerfile yang dihasilkan memakai `container.baseImage` sebagai `FROM`.
Jika `baseImage` tidak diisi, default-nya `eclipse-temurin:21-jre`.

Jika distribution tidak punya `container`, `bundleBuild` tidak membuat file
container dan akan menghapus file container generated lama dari folder output.

Untuk distribution yang hanya berisi satu module, `bundleBuild` tetap memakai
standalone mode dan tidak membuat final bundle di `build/module-composer/output`.

Command memakai distribution:

```bash
./gradlew bundleRun -Pdistribution=community
./gradlew bundleBuild -Pdistribution=enterprise
```

Output untuk contoh `enterprise`:

```text
build/module-composer/generated/enterprise-service/
build/module-composer/output/application.jar
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
-> distribution YAML applicationName
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
build/module-composer/output/application.jar
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

`Unknown distribution` berarti nama distribution tidak ada di distribution YAML
yang dikonfigurasi.

`Invalid application name` berarti nama aplikasi memakai karakter yang tidak
didukung. Gunakan huruf, angka, titik, underscore, atau dash, dan mulai dengan
huruf atau angka.

`Invalid validation` berarti `-Pvalidation` bukan `none`, `test`, atau `check`.
