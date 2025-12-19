// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <QWidget>
#include "ui/pages/BasePage.h"

namespace Ui { class BuildsPage; }

class BuildsPage : public QWidget, public BasePage
{
    Q_OBJECT
public:
    explicit BuildsPage(QWidget *parent = nullptr);
    ~BuildsPage();

    QString displayName() const override { return tr("Builds"); }
    QIcon icon() const override { return QIcon(); }
    QString id() const override { return "builds-page"; }
    QString helpPage() const override { return "builds"; }

    void retranslate() override;

private slots:
    void on_refreshBtn_clicked();
    void on_buildsList_itemActivated(class QListWidgetItem* item);
    void onBuildsUpdated();

private:
    Ui::BuildsPage *ui;
};
