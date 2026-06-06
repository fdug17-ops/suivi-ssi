Attribute VB_Name = "SSI_Macros"
Option Explicit

' ============================================================
' SUIVI SSI — Module de macros
' Auteur : généré pour Fred — RSSI
' Usage  : AfficherFormulaireAction() → bouton Dashboard
' ============================================================

' ── Constantes ───────────────────────────────────────────────
Private Const SHEET_ACTIONS As String = "Actions"
Private Const COL_ID        As Integer = 2   ' B
Private Const COL_DESC      As Integer = 3   ' C
Private Const COL_DOSSIER   As Integer = 4   ' D
Private Const COL_RESP      As Integer = 5   ' E
Private Const COL_INTERLO   As Integer = 6   ' F
Private Const COL_ENVOI     As Integer = 7   ' G
Private Const COL_STATUT    As Integer = 8   ' H
Private Const COL_ECHEANCE  As Integer = 9   ' I
Private Const COL_PRIO      As Integer = 10  ' J
Private Const COL_NOTES     As Integer = 11  ' K
Private Const FIRST_DATA_ROW As Integer = 3

' ── Point d'entrée : bouton Dashboard ────────────────────────
Public Sub AfficherFormulaireAction()
    Dim frm As New frmAction
    frm.ModeCreation
    frm.Show
End Sub

' ── Générer le prochain ID ────────────────────────────────────
Public Function ProchainID() As String
    Dim ws As Worksheet
    Dim lastRow As Long, maxNum As Long, num As Long, i As Long
    Dim cellVal As String
    
    Set ws = ThisWorkbook.Sheets(SHEET_ACTIONS)
    lastRow = ws.Cells(ws.Rows.Count, COL_ID).End(xlUp).Row
    maxNum = 0
    
    For i = FIRST_DATA_ROW To lastRow
        cellVal = CStr(ws.Cells(i, COL_ID).Value)
        If Left(cellVal, 4) = "ACT-" Then
            On Error Resume Next
            num = CLng(Mid(cellVal, 5))
            On Error GoTo 0
            If num > maxNum Then maxNum = num
        End If
    Next i
    
    ProchainID = "ACT-" & Format(maxNum + 1, "000")
End Function

' ── Écrire une action dans la feuille Actions ─────────────────
Public Sub EcrireAction(sID As String, sDesc As String, sDossier As String, _
                         sResp As String, sInterlo As String, dEnvoi As Variant, _
                         sStatut As String, dEcheance As Variant, sPrio As String, _
                         sNotes As String, Optional targetRow As Long = 0)
    Dim ws As Worksheet
    Dim r As Long
    
    Set ws = ThisWorkbook.Sheets(SHEET_ACTIONS)
    
    If targetRow = 0 Then
        ' Nouvelle action : première ligne vide après les données
        r = ws.Cells(ws.Rows.Count, COL_ID).End(xlUp).Row + 1
        If r < FIRST_DATA_ROW Then r = FIRST_DATA_ROW
    Else
        r = targetRow
    End If
    
    ws.Cells(r, COL_ID).Value      = sID
    ws.Cells(r, COL_DESC).Value    = sDesc
    ws.Cells(r, COL_DOSSIER).Value = sDossier
    ws.Cells(r, COL_RESP).Value    = sResp
    ws.Cells(r, COL_INTERLO).Value = sInterlo
    ws.Cells(r, COL_STATUT).Value  = sStatut
    ws.Cells(r, COL_PRIO).Value    = sPrio
    ws.Cells(r, COL_NOTES).Value   = sNotes
    
    If Not IsEmpty(dEnvoi) And dEnvoi <> "" Then
        ws.Cells(r, COL_ENVOI).Value = CDate(dEnvoi)
        ws.Cells(r, COL_ENVOI).NumberFormat = "DD/MM/YYYY"
    End If
    If Not IsEmpty(dEcheance) And dEcheance <> "" Then
        ws.Cells(r, COL_ECHEANCE).Value = CDate(dEcheance)
        ws.Cells(r, COL_ECHEANCE).NumberFormat = "DD/MM/YYYY"
    End If
    
    ' Appliquer la mise en forme alternée
    AppliquerFormatLigne ws, r
    
    ' Recalculer pour mettre à jour les colonnes helper L/M et le dashboard
    ws.Calculate
    ThisWorkbook.Sheets("Dashboard").Calculate
    
    MsgBox "Action " & sID & " enregistrée avec succès.", vbInformation, "Suivi SSI"
End Sub

' ── Rechercher une action par ID ──────────────────────────────
Public Function TrouverLigneParID(sID As String) As Long
    Dim ws As Worksheet
    Dim cel As Range
    
    Set ws = ThisWorkbook.Sheets(SHEET_ACTIONS)
    Set cel = ws.Columns(COL_ID).Find(What:=sID, LookAt:=xlWhole)
    
    If Not cel Is Nothing Then
        TrouverLigneParID = cel.Row
    Else
        TrouverLigneParID = 0
    End If
End Function

' ── Mise en forme d'une ligne de données ─────────────────────
Private Sub AppliquerFormatLigne(ws As Worksheet, r As Long)
    Dim col As Integer
    Dim bgColor As Long
    
    If (r Mod 2) = 1 Then
        bgColor = RGB(242, 242, 242)  ' gris clair
    Else
        bgColor = RGB(255, 255, 255)  ' blanc
    End If
    
    For col = COL_ID To COL_NOTES
        With ws.Cells(r, col)
            .Font.Name = "Arial"
            .Font.Size = 10
            .Interior.Color = bgColor
            .Borders(xlEdgeTop).LineStyle    = xlContinuous
            .Borders(xlEdgeBottom).LineStyle = xlContinuous
            .Borders(xlEdgeLeft).LineStyle   = xlContinuous
            .Borders(xlEdgeRight).LineStyle  = xlContinuous
            .Borders(xlEdgeTop).Color    = RGB(191, 191, 191)
            .Borders(xlEdgeBottom).Color = RGB(191, 191, 191)
            .Borders(xlEdgeLeft).Color   = RGB(191, 191, 191)
            .Borders(xlEdgeRight).Color  = RGB(191, 191, 191)
        End With
    Next col
End Sub

' ── Récupérer la liste des dossiers pour la combo ────────────
Public Function ListeDossiers() As String()
    Dim ws As Worksheet
    Dim lastRow As Long, i As Long, count As Long
    Dim arr() As String
    
    Set ws = ThisWorkbook.Sheets("Dossiers")
    lastRow = ws.Cells(ws.Rows.Count, 3).End(xlUp).Row  ' col C = Intitulé
    count = lastRow - 2  ' à partir de ligne 3
    
    If count < 1 Then
        ReDim arr(0)
        arr(0) = ""
        ListeDossiers = arr
        Exit Function
    End If
    
    ReDim arr(count - 1)
    For i = 0 To count - 1
        arr(i) = CStr(ws.Cells(i + 3, 3).Value)
    Next i
    
    ListeDossiers = arr
End Function
