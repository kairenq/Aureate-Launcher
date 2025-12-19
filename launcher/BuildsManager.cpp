// SPDX-License-Identifier: GPL-3.0-only
#include "BuildsManager.h"

#include <QFile>
#include <QJsonDocument>
#include <QJsonArray>
#include <QCoreApplication>
#include <QFileInfo>
#include <QDir>
#include <QStandardPaths>

#include "net/Download.h"
#include "MMCZip.h"
#include "Application.h"

BuildsManager *BuildsManager::s_instance = nullptr;

BuildsManager::BuildsManager(QObject *parent) : QObject(parent)
{
}

BuildsManager *BuildsManager::instance()
{
    if (!s_instance) {
        s_instance = new BuildsManager(qApp);
    }
    return s_instance;
}

void BuildsManager::loadFromFile(const QString &path)
{
    QString filePath = path;
    if (filePath.isEmpty()) {
        // try application dir
        filePath = QCoreApplication::applicationDirPath() + "/../builds.json";
    }

    QFile f(filePath);
    if (!f.open(QIODevice::ReadOnly)) {
        // try relative
        QFile f2("builds.json");
        if (!f2.open(QIODevice::ReadOnly)) {
            m_builds.clear();
            emit buildsUpdated();
            return;
        }
        QByteArray raw = f2.readAll();
        QJsonDocument doc = QJsonDocument::fromJson(raw);
        m_builds.clear();
        if (doc.isArray()) {
            for (auto v : doc.array()) {
                if (v.isObject()) m_builds.append(v.toObject());
            }
        }
        emit buildsUpdated();
        return;
    }

    QByteArray raw = f.readAll();
    QJsonDocument doc = QJsonDocument::fromJson(raw);
    m_builds.clear();
    if (doc.isArray()) {
        for (auto v : doc.array()) {
            if (v.isObject()) m_builds.append(v.toObject());
        }
    }
    emit buildsUpdated();
}

void BuildsManager::downloadBuild(const QString &id)
{
    // find build
    QJsonObject build;
    bool found = false;
    for (const auto &b : m_builds) {
        if (b.value("id").toString() == id) { build = b; found = true; break; }
    }
    if (!found) {
        emit downloadFailed(id, tr("Build not found"));
        return;
    }

    QString url = build.value("download_url").toString();
    if (url.isEmpty()) {
        emit downloadFailed(id, tr("No download URL"));
        return;
    }

    // determine downloads dir
    QString downloadsDir = APPLICATION->settings()->get("DownloadsDir").toString();
    if (downloadsDir.isEmpty()) {
        downloadsDir = QStandardPaths::writableLocation(QStandardPaths::DownloadLocation);
        if (downloadsDir.isEmpty()) downloadsDir = QCoreApplication::applicationDirPath();
    }
    QDir().mkpath(downloadsDir);

    QString fileName = QFileInfo(QUrl(url).path()).fileName();
    if (fileName.isEmpty()) fileName = id + ".zip";
    QString outPath = QDir(downloadsDir).filePath(fileName);

    auto dl = Net::Download::makeFile(QUrl(url), outPath);

    connect(dl.get(), &Net::Task::progress, this, [this, id](qint64 cur, qint64 tot) {
        emit downloadProgress(id, cur, tot);
    });
    connect(dl.get(), &Net::Task::succeeded, this, [this, id, outPath]() {
        // start extraction to instances dir
        QString instDir = APPLICATION->settings()->get("InstanceDir").toString();
        if (instDir.isEmpty()) instDir = QCoreApplication::applicationDirPath() + "/instances";
        QDir().mkpath(instDir);

        // extract into instance named by id
        QString target = QDir(instDir).filePath(id);
        QDir().mkpath(target);

        auto extract = new MMCZip::ExtractZipTask(outPath, QDir(target));
        connect(extract, &MMCZip::ExtractZipTask::succeeded, this, [this, id, outPath, target]() {
            emit downloadFinished(id, outPath);
        });
        connect(extract, &MMCZip::ExtractZipTask::failed, this, [this, id](QString reason) {
            emit downloadFailed(id, reason);
        });
        extract->start();
    });
    connect(dl.get(), &Net::Task::failed, this, [this, id](QString reason) {
        emit downloadFailed(id, reason);
    });

    dl->start();
}
