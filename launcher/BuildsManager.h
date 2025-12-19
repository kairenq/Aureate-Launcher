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

    QVector<QJsonObject> builds() const { return m_builds; }

signals:
    void buildsUpdated();

private:
    explicit BuildsManager(QObject *parent = nullptr);
    static BuildsManager *s_instance;

    QVector<QJsonObject> m_builds;
};
