// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <QWidget>
#include <QJsonObject>

namespace Ui { class BuildDetailPage; }

class BuildDetailPage : public QWidget
{
    Q_OBJECT
public:
    explicit BuildDetailPage(QWidget *parent = nullptr);
    ~BuildDetailPage();

    void setBuild(const QJsonObject &build);

private:
    Ui::BuildDetailPage *ui;
};
