// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <QObject>
#include <QVector>
#include <QJsonObject>

class BuildsManager : public QObject
{
    Q_OBJECT
public:
    static BuildsManager *instance();

    // Load builds from a local file or URL. For now supports local file paths.
    Q_INVOKABLE void loadFromFile(const QString &path);
    Q_INVOKABLE void downloadBuild(const QString &id);

signals:
    void downloadProgress(const QString &id, qint64 current, qint64 total);
    void downloadFinished(const QString &id, const QString &path);
    void downloadFailed(const QString &id, const QString &reason);

    QVector<QJsonObject> builds() const { return m_builds; }

signals:
    void buildsUpdated();

private:
    explicit BuildsManager(QObject *parent = nullptr);
    static BuildsManager *s_instance;

    QVector<QJsonObject> m_builds;
};
