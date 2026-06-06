VERSION 5.00
Begin {C62A69F0-16DC-11CE-9E98-00AA00574A4F} frmAction
   Caption         =   "Suivi SSI — Nouvelle action"
   ClientHeight    =   7200
   ClientLeft      =   120
   ClientTop       =   456
   ClientWidth     =   9600
   StartUpPosition =   1
End
Attribute VB_Name = "frmAction"
Attribute VB_GlobalNameSpace = False
Attribute VB_Creatable = False
Attribute VB_PredeclaredId = True
Attribute VB_Exposed = False

Option Explicit

Private mMode      As String
Private mTargetRow As Long

' ============================================================
'  INITIALISATION
' ============================================================
Private Sub UserForm_Initialize()
    Me.lblTitre.Caption  = "NOUVELLE ACTION"
    Me.txtID.Text        = ProchainID()
    Me.txtID.Locked      = True
    Me.txtID.BackColor   = RGB(220, 220, 220)

    With Me.cboStatut
        .AddItem "À faire"
        .AddItem "En cours"
        .AddItem "Relecture"
        .AddItem "Attente externe"
        .AddItem "Clôturée"
        .Value = "À faire"
    End With

    With Me.cboPriorite
        .AddItem "Haute"
        .AddItem "Moyenne"
        .AddItem "Basse"
        .Value = "Moyenne"
    End With

    Dim dossiers() As String
    dossiers = ListeDossiers()
    Me.cboDossier.AddItem ""
    Dim i As Integer
    For i = 0 To UBound(dossiers)
        If dossiers(i) <> "" Then Me.cboDossier.AddItem dossiers(i)
    Next i

    Me.AfficherChampsExternes False
    Me.txtDescription.SetFocus
End Sub

' ============================================================
'  MODES
' ============================================================
Public Sub ModeCreation()
    mMode = "CREATION"
    mTargetRow = 0
End Sub

Public Sub ModeModification(sID As String)
    mMode = "MODIFICATION"
    mTargetRow = TrouverLigneParID(sID)
    If mTargetRow = 0 Then
        MsgBox "Action " & sID & " introuvable.", vbExclamation, "Suivi SSI"
        Unload Me
        Exit Sub
    End If

    Dim ws As Worksheet
    Set ws = ThisWorkbook.Sheets("Actions")

    Me.txtID.Text          = CStr(ws.Cells(mTargetRow, 2).Value)
    Me.txtDescription.Text = CStr(ws.Cells(mTargetRow, 3).Value)
    Me.cboDossier.Value    = CStr(ws.Cells(mTargetRow, 4).Value)
    Me.txtResponsable.Text = CStr(ws.Cells(mTargetRow, 5).Value)
    Me.txtInterlocut.Text  = CStr(ws.Cells(mTargetRow, 6).Value)
    Me.cboStatut.Value     = CStr(ws.Cells(mTargetRow, 8).Value)
    Me.cboPriorite.Value   = CStr(ws.Cells(mTargetRow, 10).Value)
    Me.txtNotes.Text       = CStr(ws.Cells(mTargetRow, 11).Value)

    If ws.Cells(mTargetRow, 7).Value <> "" Then
        Me.txtDateEnvoi.Text = Format(ws.Cells(mTargetRow, 7).Value, "DD/MM/YYYY")
    End If
    If ws.Cells(mTargetRow, 9).Value <> "" Then
        Me.txtEcheance.Text = Format(ws.Cells(mTargetRow, 9).Value, "DD/MM/YYYY")
    End If

    If Me.cboStatut.Value = "Attente externe" Then Me.AfficherChampsExternes True

    Me.lblTitre.Caption = "MODIFIER L'ACTION"
    Me.Caption = "Modifier : " & Me.txtID.Text
    Me.cmdEnregistrer.Caption = "Enregistrer les modifications"
End Sub

' ============================================================
'  EVENEMENTS
' ============================================================
Private Sub cboStatut_Change()
    Me.AfficherChampsExternes (Me.cboStatut.Value = "Attente externe")
End Sub

Private Sub cmdEnregistrer_Click()
    If Trim(Me.txtDescription.Text) = "" Then
        MsgBox "La description est obligatoire.", vbExclamation, "Champ manquant"
        Me.txtDescription.SetFocus : Exit Sub
    End If
    If Trim(Me.txtResponsable.Text) = "" Then
        MsgBox "Le responsable est obligatoire.", vbExclamation, "Champ manquant"
        Me.txtResponsable.SetFocus : Exit Sub
    End If

    Dim dEnvoi As Variant, dEcheance As Variant
    dEnvoi = "" : dEcheance = ""

    If Trim(Me.txtDateEnvoi.Text) <> "" Then
        If Not EstDateValide(Me.txtDateEnvoi.Text) Then
            MsgBox "Date d'envoi invalide. Format : JJ/MM/AAAA", vbExclamation
            Me.txtDateEnvoi.SetFocus : Exit Sub
        End If
        dEnvoi = CDate(Me.txtDateEnvoi.Text)
    End If

    If Trim(Me.txtEcheance.Text) <> "" Then
        If Not EstDateValide(Me.txtEcheance.Text) Then
            MsgBox "Echeance invalide. Format : JJ/MM/AAAA", vbExclamation
            Me.txtEcheance.SetFocus : Exit Sub
        End If
        dEcheance = CDate(Me.txtEcheance.Text)
    End If

    EcrireAction Me.txtID.Text, Trim(Me.txtDescription.Text), _
        Me.cboDossier.Value, Trim(Me.txtResponsable.Text), _
        Trim(Me.txtInterlocut.Text), dEnvoi, Me.cboStatut.Value, _
        dEcheance, Me.cboPriorite.Value, Trim(Me.txtNotes.Text), mTargetRow

    If mMode = "CREATION" Then
        If MsgBox("Saisir une autre action ?", vbYesNo + vbQuestion, "Suivi SSI") = vbYes Then
            ReinitialiserFormulaire
        Else
            Unload Me
        End If
    Else
        Unload Me
    End If
End Sub

Private Sub cmdRechercher_Click()
    Dim sID As String
    sID = Trim(InputBox("Entrez l'ID de l'action à modifier (ex: ACT-003) :", "Modifier une action"))
    If sID = "" Then Exit Sub
    If TrouverLigneParID(sID) = 0 Then
        MsgBox "Action " & sID & " introuvable.", vbExclamation
    Else
        Call ModeModification(sID)
    End If
End Sub

Private Sub cmdAnnuler_Click()
    Unload Me
End Sub

' ============================================================
'  METHODES PRIVEES
' ============================================================
Private Sub AfficherChampsExternes(bVisible As Boolean)
    Me.lblInterlocut.Visible = bVisible
    Me.txtInterlocut.Visible = bVisible
    Me.lblDateEnvoi.Visible  = bVisible
    Me.txtDateEnvoi.Visible  = bVisible
    If bVisible Then Me.Height = 440 Else Me.Height = 375
End Sub

Private Sub ReinitialiserFormulaire()
    Me.txtID.Text = ProchainID()
    Me.txtDescription.Text = ""
    Me.cboDossier.Value    = ""
    Me.txtResponsable.Text = ""
    Me.txtInterlocut.Text  = ""
    Me.txtDateEnvoi.Text   = ""
    Me.cboStatut.Value     = "À faire"
    Me.txtEcheance.Text    = ""
    Me.cboPriorite.Value   = "Moyenne"
    Me.txtNotes.Text       = ""
    Me.AfficherChampsExternes False
    mMode = "CREATION" : mTargetRow = 0
    Me.txtDescription.SetFocus
End Sub

Private Function EstDateValide(sDate As String) As Boolean
    On Error Resume Next
    Dim d As Date
    d = CDate(sDate)
    EstDateValide = (Err.Number = 0)
    On Error GoTo 0
End Function
