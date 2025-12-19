// SPDX-License-Identifier: GPL-3.0-only
#include "BuildsManager.h"

#include <QFile>
#include <QJsonDocument>
#include <QJsonArray>
#include <QCoreApplication>

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
