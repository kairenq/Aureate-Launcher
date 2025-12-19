// SPDX-License-Identifier: GPL-3.0-only
#include "BuildDetailPage.h"
#include "ui_BuildDetailPage.h"
#include "BuildsManager.h"

#include <QJsonObject>

BuildDetailPage::BuildDetailPage(QWidget *parent) : QWidget(parent), ui(new Ui::BuildDetailPage)
{
    ui->setupUi(this);
}

BuildDetailPage::~BuildDetailPage()
{
    delete ui;
}

void BuildDetailPage::setBuild(const QJsonObject &build)
{
    ui->titleLabel->setText(build.value("name").toString());
    ui->descriptionText->setPlainText(build.value("description").toString());
    // buttons: download/launch to be implemented later
}
