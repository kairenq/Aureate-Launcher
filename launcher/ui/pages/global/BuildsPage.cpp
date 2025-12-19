// SPDX-License-Identifier: GPL-3.0-only
#include "BuildsPage.h"
#include "ui_BuildsPage.h"
#include "BuildsManager.h"

#include <QListWidgetItem>
#include <QJsonObject>
#include <QJsonValue>
#include <QTextEdit>
#include <QVBoxLayout>

BuildsPage::BuildsPage(QWidget *parent) : QWidget(parent), ui(new Ui::BuildsPage)
{
    ui->setupUi(this);

    connect(ui->refreshBtn, &QPushButton::clicked, this, &BuildsPage::on_refreshBtn_clicked);
    connect(BuildsManager::instance(), &BuildsManager::buildsUpdated, this, &BuildsPage::onBuildsUpdated);

    // initial load
    BuildsManager::instance()->loadFromFile("");
}

BuildsPage::~BuildsPage()
{
    delete ui;
}

void BuildsPage::retranslate()
{
    ui->retranslateUi(this);
}

void BuildsPage::on_refreshBtn_clicked()
{
    ui->statusLabel->setText(tr("Loading..."));
    BuildsManager::instance()->loadFromFile("");
}

void BuildsPage::onBuildsUpdated()
{
    ui->statusLabel->clear();
    ui->buildsList->clear();
    for (const QJsonObject &b : BuildsManager::instance()->builds()) {
        QString name = b.value("name").toString(b.value("id").toString());
        auto *it = new QListWidgetItem(name, ui->buildsList);
        it->setData(Qt::UserRole, b.value("id").toString());
        it->setToolTip(b.value("summary").toString());
    }
}

void BuildsPage::on_buildsList_itemActivated(QListWidgetItem* item)
{
    if (!item) return;
    QString id = item->data(Qt::UserRole).toString();
    // find build
    for (const QJsonObject &b : BuildsManager::instance()->builds()) {
        if (b.value("id").toString() == id) {
            // show detail dialog
            QWidget *dlg = new QWidget(nullptr);
            dlg->setWindowTitle(b.value("name").toString());
            dlg->resize(600,400);
            // simple display
            auto *text = new QTextEdit(dlg);
            text->setReadOnly(true);
            text->setPlainText(b.value("description").toString());
            auto *layout = new QVBoxLayout(dlg);
            layout->addWidget(text);
            dlg->setLayout(layout);
            dlg->show();
            break;
        }
    }
}
